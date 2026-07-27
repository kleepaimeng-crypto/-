package com.cabin.flighthistory.service;

import com.cabin.flighthistory.entity.ArchiveJobRow;
import com.cabin.flighthistory.entity.FlightHistorySessionRow;
import com.cabin.flighthistory.mapper.FlightHistoryArchiveMapper;
import com.cabin.flighttrack.entity.FlightSessionRow;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "cabin.flight-history.enabled", havingValue = "true", matchIfMissing = true)
public class FlightHistoryArchiveWorker {
    private final FlightHistoryArchiveMapper mapper;
    private final Clock clock;

    @Autowired
    public FlightHistoryArchiveWorker(FlightHistoryArchiveMapper mapper) {
        this(mapper, Clock.systemDefaultZone());
    }

    FlightHistoryArchiveWorker(FlightHistoryArchiveMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public boolean archiveNext() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        ArchiveJobRow job = mapper.lockNextJob(now);
        if (job == null) {
            return false;
        }
        mapper.markRunning(job.getId(), now);
        FlightSessionRow source = mapper.findFinishedSourceSession(job.getSourceFlightSessionId());
        if (source == null) {
            throw new IllegalStateException("归档来源会话不存在或未结束");
        }
        UUID archiveId = mapper.findArchiveId(source.getId());
        if (archiveId == null) {
            archiveId = UUID.randomUUID();
            FlightHistorySessionRow archive = new FlightHistorySessionRow();
            archive.setId(archiveId);
            archive.setSourceFlightSessionId(source.getId());
            archive.setSourceSystemCode(source.getSourceSystemCode());
            archive.setSourceDeviceCode(source.getSourceDeviceCode());
            archive.setSourceHost(source.getSourceHost());
            archive.setFlightNo(source.getFlightNo());
            archive.setOrigin(source.getOrigin());
            archive.setDestination(source.getDestination());
            archive.setAircraftRegistrationNo(source.getAircraftRegistrationNo());
            archive.setAircraftModel(source.getAircraftModel());
            archive.setAirlineCode(source.getAirlineCode());
            archive.setStartedAt(source.getStartedAt());
            archive.setEndedAt(source.getEndedAt());
            archive.setFinishReason(job.getFinishReason());
            mapper.insertArchiveSession(archive);
            archiveId = mapper.findArchiveId(source.getId());
        }
        mapper.copySourcePoints(archiveId, source.getId());
        mapper.updatePointCount(archiveId);
        mapper.markSucceeded(job.getId(), now);
        return true;
    }
}
