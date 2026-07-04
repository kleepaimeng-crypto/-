package com.cabin.udp.service;

import com.cabin.config.UdpProperties;
import com.cabin.udp.mapper.DataTypeMapper;
import com.cabin.udp.entity.DataTypeConfig;
import com.cabin.udp.dto.UdpChannelSnapshot;
import com.cabin.udp.dto.UdpIngestOutcome;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class UdpIngestListener implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(UdpIngestListener.class);

    private final UdpProperties properties;
    private final ObjectProvider<DataTypeMapper> dataTypeMapperProvider;
    private final UdpIngestService ingestService;
    private final Map<Integer, UdpChannelStatus> statuses = new ConcurrentHashMap<>();
    private final List<DatagramSocket> sockets = Collections.synchronizedList(new ArrayList<>());
    private final List<Thread> workers = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean running;

    public UdpIngestListener(
            UdpProperties properties,
            ObjectProvider<DataTypeMapper> dataTypeMapperProvider,
            UdpIngestService ingestService
    ) {
        this.properties = properties;
        this.dataTypeMapperProvider = dataTypeMapperProvider;
        this.ingestService = ingestService;
    }

    @Override
    public void start() {
        if (!properties.enabled() || running) {
            return;
        }
        DataTypeMapper mapper = dataTypeMapperProvider.getIfAvailable();
        if (mapper == null) {
            log.warn("UDP ingest is enabled but data_type mapper is unavailable; listener startup skipped");
            return;
        }

        List<DataTypeConfig> configs;
        try {
            configs = mapper.findEnabledUdpTypes();
        } catch (RuntimeException exception) {
            log.warn("UDP ingest listener startup skipped because data_type config cannot be loaded");
            return;
        }

        running = true;
        for (DataTypeConfig config : configs) {
            startChannel(config);
        }
        if (workers.isEmpty()) {
            running = false;
        }
    }

    @Override
    public void stop() {
        running = false;
        synchronized (sockets) {
            for (DatagramSocket socket : sockets) {
                socket.close();
            }
            sockets.clear();
        }
        synchronized (workers) {
            for (Thread worker : workers) {
                worker.interrupt();
            }
            workers.clear();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    public List<UdpChannelSnapshot> snapshots() {
        return statuses.values()
                .stream()
                .map(UdpChannelStatus::snapshot)
                .sorted((left, right) -> Integer.compare(left.port(), right.port()))
                .toList();
    }

    private void startChannel(DataTypeConfig config) {
        if (config.getUdpPort() == null) {
            return;
        }
        try {
            DatagramSocket socket = new DatagramSocket(config.getUdpPort());
            socket.setReceiveBufferSize(properties.socketReceiveBufferBytes());
            sockets.add(socket);

            UdpChannelStatus status = new UdpChannelStatus(config);
            statuses.put(config.getUdpPort(), status);
            Thread worker = Thread.ofPlatform()
                    .name("udp-" + config.getUdpPort())
                    .daemon(true)
                    .start(() -> receiveLoop(socket, config, status));
            workers.add(worker);
            log.info("UDP ingest channel started: {} on {}", config.getMessageType(), config.getUdpPort());
        } catch (SocketException exception) {
            log.warn("UDP ingest channel {} on {} cannot start: {}",
                    config.getMessageType(),
                    config.getUdpPort(),
                    exception.getMessage());
        }
    }

    private void receiveLoop(DatagramSocket socket, DataTypeConfig config, UdpChannelStatus status) {
        while (running && !socket.isClosed()) {
            byte[] buffer = new byte[properties.maxDatagramBytes()];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            OffsetDateTime receivedAt = OffsetDateTime.now(properties.zone());
            try {
                socket.receive(packet);
                receivedAt = OffsetDateTime.now(properties.zone());
                status.markReceived(receivedAt);
                UdpIngestOutcome outcome = ingestService.ingestDatagram(
                        config,
                        packet.getData(),
                        packet.getLength(),
                        receivedAt,
                        packet.getAddress().getHostAddress(),
                        packet.getPort()
                );
                if (outcome.failed()) {
                    status.markFailure(receivedAt, outcome.errorMessage());
                } else {
                    status.markSuccess(receivedAt);
                }
            } catch (SocketException exception) {
                if (running) {
                    status.markFailure(receivedAt, exception.getMessage());
                }
            } catch (IOException exception) {
                status.markFailure(receivedAt, exception.getMessage());
            } catch (RuntimeException exception) {
                status.markFailure(receivedAt, safeMessage(exception));
            }
        }
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}



