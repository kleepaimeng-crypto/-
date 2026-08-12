package com.cabin.udp.service;

import com.cabin.udp.mapper.UdpIngestMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QarBackfillTaskService {
    private static final Logger log = LoggerFactory.getLogger(QarBackfillTaskService.class);

    private final ObjectProvider<UdpIngestMapper> mapperProvider;

    public QarBackfillTaskService(ObjectProvider<UdpIngestMapper> mapperProvider) {
        this.mapperProvider = mapperProvider;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void backfill(QarBackfillRequest request) {
        UdpIngestMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            return;
        }
        long startedAt = System.nanoTime();
        int flightContextRows = mapper.backfillMissingFlightContext(
                request.flightNo(),
                request.origin(),
                request.destination(),
                request.airlineCode(),
                request.applicationStartedAt()
        );
        int cockrellRows = mapper.backfillPendingCockrellSession(request.flightSessionId());
        int ife633Rows = mapper.backfillPendingIfe633Session(
                request.flightSessionId(),
                request.applicationStartedAt()
        );
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "QAR post-commit backfill completed for session {}: contextRows={}, cockrellRows={}, ife633Rows={}, elapsedMs={}",
                request.flightSessionId(),
                flightContextRows,
                cockrellRows,
                ife633Rows,
                elapsedMillis
        );
    }
}
