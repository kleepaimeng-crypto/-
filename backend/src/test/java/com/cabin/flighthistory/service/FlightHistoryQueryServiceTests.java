package com.cabin.flighthistory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cabin.common.exception.BusinessException;
import com.cabin.flighthistory.entity.FlightHistoryPointRow;
import com.cabin.flighthistory.entity.FlightHistorySessionRow;
import com.cabin.flighthistory.mapper.FlightHistoryQueryMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlightHistoryQueryServiceTests {
    private final FlightHistoryQueryMapper mapper = mock(FlightHistoryQueryMapper.class);
    private final OffsetDateTime now = OffsetDateTime.parse("2026-07-27T10:00:00+08:00");
    private final FlightHistoryQueryService service = new FlightHistoryQueryService(
            mapper, Clock.fixed(now.toInstant(), ZoneId.of("Asia/Shanghai"))
    );

    @Test
    void samplesTrackButKeepsBothEnds() {
        UUID id = UUID.randomUUID();
        FlightHistorySessionRow session = session(id);
        List<FlightHistoryPointRow> points = new ArrayList<>();
        for (int index = 0; index < 10; index += 1) points.add(point(session.getStartedAt().plusMinutes(index), index));
        when(mapper.findSession(id)).thenReturn(session);
        when(mapper.findTrack(id, session.getStartedAt(), session.getEndedAt())).thenReturn(points);

        var result = service.getTrack(id, null, null, 3);

        assertThat(result.sourcePointCount()).isEqualTo(10);
        assertThat(result.returnedPointCount()).isEqualTo(3);
        assertThat(result.sampled()).isTrue();
        assertThat(result.track().getFirst().sampleAt()).isEqualTo(points.getFirst().getSampleAt());
        assertThat(result.track().getLast().sampleAt()).isEqualTo(points.getLast().getSampleAt());
    }

    @Test
    void rejectsAQueryWithoutAnEndTime() {
        assertThatThrownBy(() -> service.list(null, null, null, null, null, null, null, 1, 20, "endedAt", "desc"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("至少提供一个完成时间范围");
    }

    private FlightHistorySessionRow session(UUID id) {
        FlightHistorySessionRow row = new FlightHistorySessionRow();
        row.setId(id);
        row.setFlightNo("CA4732"); row.setOrigin("ZBAA"); row.setDestination("ZSHC");
        row.setAircraftRegistrationNo("B-TEST-001"); row.setStartedAt(now.minusMinutes(10)); row.setEndedAt(now);
        row.setPointCount(10); row.setFinishReason("LANDED"); row.setArchivedAt(now);
        return row;
    }

    private FlightHistoryPointRow point(OffsetDateTime sampleAt, int frame) {
        FlightHistoryPointRow row = new FlightHistoryPointRow();
        row.setSampleAt(sampleAt); row.setSourceTimeText(sampleAt.toLocalTime().withNano(0).toString());
        row.setFrameCount((long) frame); row.setAirGroundStatus("AIR"); row.setLatitude(30.0); row.setLongitude(120.0);
        return row;
    }
}
