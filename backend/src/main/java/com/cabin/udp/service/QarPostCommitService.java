package com.cabin.udp.service;

import com.cabin.udp.dto.CurrentFlightContext;
import com.cabin.udp.entity.DataRecord;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QarPostCommitService {
    private final CurrentFlightContextService currentFlightContextService;
    private final QarBackfillScheduler backfillScheduler;

    public QarPostCommitService(
            CurrentFlightContextService currentFlightContextService,
            QarBackfillScheduler backfillScheduler
    ) {
        this.currentFlightContextService = currentFlightContextService;
        this.backfillScheduler = backfillScheduler;
    }

    public void handle(
            DataRecord record,
            UUID flightSessionId,
            OffsetDateTime sessionStartedAt
    ) {
        CurrentFlightContext before = currentFlightContextService.current();
        CurrentFlightContext context = currentFlightContextService.updateFromQar(
                record,
                flightSessionId,
                sessionStartedAt
        );
        boolean newSession = flightSessionId != null
                && (before == null || !Objects.equals(before.flightSessionId(), flightSessionId));
        if (newSession && context != null && context.hasRoute()) {
            backfillScheduler.schedule(new QarBackfillRequest(
                    flightSessionId,
                    context.flightNo(),
                    context.origin(),
                    context.destination(),
                    context.airlineCode(),
                    currentFlightContextService.startedAt()
            ));
        }
    }
}
