package com.cabin.udp.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class UdpIngestInfoContributor implements InfoContributor {
    private final UdpIngestListener listener;

    public UdpIngestInfoContributor(UdpIngestListener listener) {
        this.listener = listener;
    }

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> ingest = new LinkedHashMap<>();
        ingest.put("running", listener.isRunning());
        ingest.put("channels", listener.snapshots());
        builder.withDetail("udpIngest", ingest);
    }
}

