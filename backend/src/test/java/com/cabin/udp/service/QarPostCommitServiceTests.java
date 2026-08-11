package com.cabin.udp.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.cabin.config.UdpProperties;
import com.cabin.udp.entity.DataRecord;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QarPostCommitServiceTests {
    private final CurrentFlightContextService contextService = new CurrentFlightContextService(
            new UdpProperties(false, 0, 0, null, null, null, null)
    );
    private final QarBackfillScheduler scheduler = mock(QarBackfillScheduler.class);
    private final QarPostCommitService service = new QarPostCommitService(contextService, scheduler);

    @Test
    void schedulesBackfillOnlyWhenFlightSessionChanges() {
        UUID sessionId = UUID.randomUUID();
        OffsetDateTime receivedAt = OffsetDateTime.parse("2026-08-11T13:00:00+08:00");

        service.handle(record(receivedAt), sessionId, receivedAt);
        service.handle(record(receivedAt.plusSeconds(10)), sessionId, receivedAt);

        verify(scheduler, times(1)).schedule(any(QarBackfillRequest.class));
    }

    private DataRecord record(OffsetDateTime receivedAt) {
        DataRecord record = new DataRecord();
        record.setFlightNo("CA4732");
        record.setOrigin("ZBAA");
        record.setDestination("ZSPD");
        record.setAirlineCode("CA");
        record.setReceivedAt(receivedAt);
        return record;
    }
}
