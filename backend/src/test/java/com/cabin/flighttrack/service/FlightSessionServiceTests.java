package com.cabin.flighttrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cabin.flighttrack.entity.FlightSessionRow;
import com.cabin.flighttrack.mapper.FlightSessionMapper;
import com.cabin.udp.entity.DataRecord;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class FlightSessionServiceTests {
    private final FlightSessionMapper mapper = mock(FlightSessionMapper.class);
    private final FlightSessionService service = new FlightSessionService(provider(mapper));
    private final OffsetDateTime now = OffsetDateTime.parse("2026-07-09T10:00:00+08:00");

    @Test
    void createsSessionForFirstQarPoint() {
        UUID sessionId = service.resolve(record(now), qarRow(now, 1));

        ArgumentCaptor<FlightSessionRow> captor = ArgumentCaptor.forClass(FlightSessionRow.class);
        verify(mapper).insert(captor.capture());
        assertThat(sessionId).isEqualTo(captor.getValue().getId());
        assertThat(captor.getValue().getLastFrameCount()).isEqualTo(1);
    }

    @Test
    void keepsActiveSessionForIncreasingFrames() {
        FlightSessionRow active = activeSession(now.minusSeconds(10), 10);
        when(mapper.findActiveForUpdate("SIMULATOR", "SIM-QAR", "127.0.0.1"))
                .thenReturn(active);

        UUID sessionId = service.resolve(record(now), qarRow(now, 11));

        assertThat(sessionId).isEqualTo(active.getId());
        verify(mapper, never()).finish(any());
        verify(mapper, never()).insert(any());
    }

    @Test
    void startsNewSessionWhenSimulatorFrameResets() {
        FlightSessionRow active = activeSession(now.minusSeconds(10), 200);
        when(mapper.findActiveForUpdate("SIMULATOR", "SIM-QAR", "127.0.0.1"))
                .thenReturn(active);

        UUID sessionId = service.resolve(record(now), qarRow(now, 1));

        assertThat(sessionId).isNotEqualTo(active.getId());
        verify(mapper).finish(active.getId());
        verify(mapper).insert(any());
    }

    @Test
    void startsNewSessionAfterFiveMinuteGap() {
        FlightSessionRow active = activeSession(now.minusMinutes(6), 10);
        when(mapper.findActiveForUpdate("SIMULATOR", "SIM-QAR", "127.0.0.1"))
                .thenReturn(active);

        service.resolve(record(now), qarRow(now, 11));

        verify(mapper).finish(active.getId());
        verify(mapper).insert(any());
    }

    private FlightSessionRow activeSession(OffsetDateTime lastAt, long frameCount) {
        FlightSessionRow row = new FlightSessionRow();
        row.setId(UUID.randomUUID());
        row.setFlightNo("CA8533");
        row.setOrigin("ZSHC");
        row.setDestination("ZBAA");
        row.setLastSampleAt(lastAt);
        row.setLastReceivedAt(lastAt);
        row.setLastFrameCount(frameCount);
        return row;
    }

    private DataRecord record(OffsetDateTime receivedAt) {
        DataRecord record = new DataRecord();
        record.setId(UUID.randomUUID());
        record.setSourceSystemCode("SIMULATOR");
        record.setSourceDeviceCode("SIM-QAR");
        record.setSourceHost("127.0.0.1");
        record.setFlightNo("CA8533");
        record.setOrigin("ZSHC");
        record.setDestination("ZBAA");
        record.setAircraftRegistrationNo("B-TEST-001");
        record.setAircraftModel("Boeing 777-300ER");
        record.setAirlineCode("CA");
        record.setReceivedAt(receivedAt);
        return record;
    }

    private Map<String, Object> qarRow(OffsetDateTime sampleAt, long frameCount) {
        Map<String, Object> row = new HashMap<>();
        row.put("sampleAt", sampleAt);
        row.put("frameCount", frameCount);
        return row;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<FlightSessionMapper> provider(FlightSessionMapper mapper) {
        ObjectProvider<FlightSessionMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        return provider;
    }
}
