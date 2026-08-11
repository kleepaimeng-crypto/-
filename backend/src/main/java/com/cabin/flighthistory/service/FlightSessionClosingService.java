package com.cabin.flighthistory.service;

import com.cabin.flighthistory.FlightFinishReason;
import com.cabin.flighthistory.entity.FlightHistoryPointRow;
import com.cabin.flighthistory.mapper.FlightHistoryArchiveMapper;
import com.cabin.flighttrack.entity.FlightSessionRow;
import com.cabin.flighttrack.mapper.FlightSessionMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "cabin.flight-history.enabled", havingValue = "true", matchIfMissing = true)
public class FlightSessionClosingService {
    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    private static final Duration LANDING_WINDOW = Duration.ofMinutes(5);
    private static final Duration MAX_LANDING_INTERVAL = Duration.ofSeconds(60);
    private final FlightSessionMapper sessionMapper;
    private final FlightHistoryArchiveMapper archiveMapper;
    private final Clock clock;

    @Autowired
    public FlightSessionClosingService(
            FlightSessionMapper sessionMapper,
            FlightHistoryArchiveMapper archiveMapper
    ) {
        this(sessionMapper, archiveMapper, Clock.systemDefaultZone());
    }

    FlightSessionClosingService(
            FlightSessionMapper sessionMapper,
            FlightHistoryArchiveMapper archiveMapper,
            Clock clock
    ) {
        this.sessionMapper = sessionMapper;
        this.archiveMapper = archiveMapper;
        this.clock = clock;
    }

    public void closeLocked(FlightSessionRow active, FlightFinishReason reason) {
        if (sessionMapper.finish(active.getId()) == 1) {
            archiveMapper.insertArchiveJob(UUID.randomUUID(), active.getId(), reason.name());
        }
    }

    @Transactional
    public void closeDueSessions() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (FlightSessionRow candidate : archiveMapper.findActiveSessions()) {
            String streamKey = candidate.getSourceSystemCode() + "|" + candidate.getSourceDeviceCode()
                    + "|" + candidate.getSourceHost();
            sessionMapper.lockStream(streamKey);
            FlightSessionRow active = sessionMapper.findActiveForUpdate(
                    candidate.getSourceSystemCode(), candidate.getSourceDeviceCode(), candidate.getSourceHost()
            );
            if (active == null) {
                continue;
            }
            FlightFinishReason reason = closingReason(active, now);
            if (reason != null) {
                closeLocked(active, reason);
            }
        }
    }

    private FlightFinishReason closingReason(FlightSessionRow active, OffsetDateTime now) {
        if (active.getLastReceivedAt().isBefore(now.minus(TIMEOUT))) {
            return FlightFinishReason.TIMEOUT;
        }
        List<FlightHistoryPointRow> points = archiveMapper.findSourcePointsSince(
                active.getId(), active.getLastSampleAt().minus(LANDING_WINDOW)
        );
        if (points.isEmpty() || points.getFirst().getSampleAt().isAfter(active.getLastSampleAt().minus(LANDING_WINDOW))) {
            return null;
        }
        FlightHistoryPointRow previous = null;
        for (FlightHistoryPointRow point : points) {
            if (!"GROUND".equalsIgnoreCase(point.getAirGroundStatus())
                    || exceeds(point.getGroundSpeedKt(), "40") || exceeds(point.getAltitudeFt(), "200")) {
                return null;
            }
            if (previous != null && Duration.between(previous.getSampleAt(), point.getSampleAt())
                    .compareTo(MAX_LANDING_INTERVAL) > 0) {
                return null;
            }
            previous = point;
        }
        return FlightFinishReason.LANDED;
    }

    private boolean exceeds(BigDecimal value, String maximum) {
        return value == null || value.compareTo(new BigDecimal(maximum)) > 0;
    }
}
