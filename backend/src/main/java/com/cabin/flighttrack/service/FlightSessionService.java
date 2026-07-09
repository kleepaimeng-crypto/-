package com.cabin.flighttrack.service;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ResponseCode;
import com.cabin.flighttrack.entity.FlightSessionRow;
import com.cabin.flighttrack.mapper.FlightSessionMapper;
import com.cabin.udp.entity.DataRecord;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class FlightSessionService {
    private static final Duration SESSION_GAP = Duration.ofMinutes(5);
    private static final long RESET_FRAME_MAX = 5;
    private static final String UNKNOWN_HOST = "0.0.0.0";

    private final ObjectProvider<FlightSessionMapper> mapperProvider;

    public FlightSessionService(ObjectProvider<FlightSessionMapper> mapperProvider) {
        this.mapperProvider = mapperProvider;
    }

    public UUID resolve(DataRecord record, Map<String, Object> qarRow) {
        String sourceHost = normalizeHost(record.getSourceHost());
        String streamKey = record.getSourceSystemCode() + "|"
                + record.getSourceDeviceCode() + "|" + sourceHost;
        FlightSessionMapper mapper = mapper();
        mapper.lockStream(streamKey);
        FlightSessionRow active = mapper.findActiveForUpdate(
                record.getSourceSystemCode(),
                record.getSourceDeviceCode(),
                sourceHost
        );

        OffsetDateTime sampleAt = required(qarRow, "sampleAt", OffsetDateTime.class);
        long frameCount = requiredNumber(qarRow, "frameCount");
        if (active != null && continues(active, record, sampleAt, frameCount)) {
            return active.getId();
        }
        if (active != null) {
            mapper.finish(active.getId());
        }

        FlightSessionRow created = new FlightSessionRow();
        created.setId(UUID.randomUUID());
        created.setSourceSystemCode(record.getSourceSystemCode());
        created.setSourceDeviceCode(record.getSourceDeviceCode());
        created.setSourceHost(sourceHost);
        created.setFlightNo(requiredText(record.getFlightNo(), "flightNo"));
        created.setOrigin(requiredText(record.getOrigin(), "origin"));
        created.setDestination(requiredText(record.getDestination(), "destination"));
        created.setAircraftRegistrationNo(requiredText(
                record.getAircraftRegistrationNo(),
                "aircraftRegistrationNo"
        ));
        created.setAircraftModel(record.getAircraftModel());
        created.setAirlineCode(record.getAirlineCode());
        created.setStatus("ACTIVE");
        created.setStartedAt(sampleAt);
        created.setLastSampleAt(sampleAt);
        created.setLastReceivedAt(record.getReceivedAt());
        created.setLastFrameCount(frameCount);
        mapper.insert(created);
        return created.getId();
    }

    public void updateLatest(
            UUID sessionId,
            DataRecord record,
            Map<String, Object> qarRow
    ) {
        mapper().updateLatest(
                sessionId,
                record.getId(),
                required(qarRow, "sampleAt", OffsetDateTime.class),
                record.getReceivedAt(),
                requiredNumber(qarRow, "frameCount")
        );
    }

    private boolean continues(
            FlightSessionRow active,
            DataRecord record,
            OffsetDateTime sampleAt,
            long frameCount
    ) {
        if (!same(active.getFlightNo(), record.getFlightNo())
                || !same(active.getOrigin(), record.getOrigin())
                || !same(active.getDestination(), record.getDestination())) {
            return false;
        }
        if (frameCount <= RESET_FRAME_MAX && active.getLastFrameCount() > frameCount) {
            return false;
        }
        Duration receivedGap = Duration.between(active.getLastReceivedAt(), record.getReceivedAt());
        Duration sampleGap = Duration.between(active.getLastSampleAt(), sampleAt);
        return receivedGap.compareTo(SESSION_GAP) <= 0 && sampleGap.compareTo(SESSION_GAP) <= 0;
    }

    private FlightSessionMapper mapper() {
        FlightSessionMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "飞行会话数据库暂不可用");
        }
        return mapper;
    }

    private String normalizeHost(String sourceHost) {
        return sourceHost == null || sourceHost.isBlank() ? UNKNOWN_HOST : sourceHost;
    }

    private boolean same(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required for flight session");
        }
        return value;
    }

    private long requiredNumber(Map<String, Object> row, String field) {
        Object value = row.get(field);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException(field + " is required for flight session");
    }

    private <T> T required(Map<String, Object> row, String field, Class<T> type) {
        Object value = row.get(field);
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        throw new IllegalArgumentException(field + " is required for flight session");
    }
}
