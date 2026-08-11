package com.cabin.udp.service;

import com.cabin.udp.dto.CurrentFlightContext;
import com.cabin.udp.entity.DataRecord;
import com.cabin.udp.mapper.UdpIngestMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QarPostCommitService {
    private final ObjectProvider<UdpIngestMapper> mapperProvider;
    private final CurrentFlightContextService currentFlightContextService;

    public QarPostCommitService(
            ObjectProvider<UdpIngestMapper> mapperProvider,
            CurrentFlightContextService currentFlightContextService
    ) {
        this.mapperProvider = mapperProvider;
        this.currentFlightContextService = currentFlightContextService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(
            DataRecord record,
            UUID flightSessionId,
            OffsetDateTime sessionStartedAt
    ) {
        CurrentFlightContext context = currentFlightContextService.updateFromQar(
                record,
                flightSessionId,
                sessionStartedAt
        );
        UdpIngestMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        if (context != null && context.hasRoute()) {
            mapper.backfillMissingFlightContext(
                    context.flightNo(),
                    context.origin(),
                    context.destination(),
                    context.airlineCode(),
                    currentFlightContextService.startedAt()
            );
        }
        if (flightSessionId != null) {
            mapper.backfillPendingCockrellSession(flightSessionId);
        }
    }
}
