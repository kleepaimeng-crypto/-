package com.cabin.flighttrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cabin.flighttrack.entity.FlightTrackPointRow;
import com.cabin.flighttrack.mapper.FlightTrackMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class FlightTrackServiceTests {
    private final FlightTrackMapper mapper = mock(FlightTrackMapper.class);
    private final OffsetDateTime now = OffsetDateTime.parse("2026-07-08T10:00:00+08:00");
    private final FlightTrackService service = new FlightTrackService(
            provider(mapper),
            Clock.fixed(now.toInstant(), ZoneId.of("Asia/Shanghai"))
    );

    @Test
    void returnsNullWhenNoCurrentQarExists() {
        when(mapper.findCurrentState()).thenReturn(null);
        when(mapper.findLatestQarState(now.minusSeconds(300))).thenReturn(null);

        assertThat(service.getCurrent()).isNull();
    }

    @Test
    void returnsNullWhenCurrentQarIsNotFlying() {
        FlightTrackPointRow row = row(now.minusSeconds(10), "80");
        when(mapper.findCurrentState()).thenReturn(row);

        assertThat(service.getCurrent()).isNull();
    }

    @Test
    void assemblesCurrentFlightAndAscendingTrack() {
        FlightTrackPointRow first = row(now.minusMinutes(1), "455");
        first.setFrameCount(41L);
        first.setLatitude(35.5);
        first.setLongitude(119.8);
        FlightTrackPointRow latest = row(now, "470");
        first.setRecordId(latest.getRecordId());
        when(mapper.findCurrentState()).thenReturn(latest);
        when(mapper.findTrack("CA4732", now.minusHours(24), now))
                .thenReturn(List.of(first, latest));

        var result = service.getCurrent();

        assertThat(result).isNotNull();
        assertThat(result.flight().flightNo()).isEqualTo("CA4732");
        assertThat(result.flight().originAirportName()).isEqualTo("北京首都国际机场");
        assertThat(result.flight().airlineName()).isEqualTo("中国国际航空");
        assertThat(result.track()).hasSize(2);
        assertThat(result.startAt()).isEqualTo(first.getSampleAt());
        assertThat(result.endAt()).isEqualTo(latest.getSampleAt());
        assertThat(result.latestPoint()).isEqualTo(result.track().getLast());
        assertThat(result.pollIntervalSeconds()).isEqualTo(5);
        assertThat(result.freshnessSeconds()).isEqualTo(300);
    }

    @Test
    void fallsBackToQarSampleWhenStateTableIsStale() {
        FlightTrackPointRow stale = row(now.minusMinutes(10), "470");
        FlightTrackPointRow latest = row(now.minusSeconds(30), "470");
        when(mapper.findCurrentState()).thenReturn(stale);
        when(mapper.findLatestQarState(now.minusSeconds(300))).thenReturn(latest);
        when(mapper.findTrack("CA4732", latest.getSampleAt().minusHours(24), latest.getSampleAt()))
                .thenReturn(List.of(latest));

        var result = service.getCurrent();

        assertThat(result).isNotNull();
        assertThat(result.latestPoint().sampleAt()).isEqualTo(latest.getSampleAt());
    }

    @Test
    void samplesLongTrackButKeepsLatestPoint() {
        FlightTrackPointRow latest = row(now, "470");
        List<FlightTrackPointRow> rows = new ArrayList<>();
        for (int index = 0; index < 800; index++) {
            FlightTrackPointRow point = row(now.minusMinutes(799L - index), "470");
            point.setLatitude(30.0 + index * 0.001);
            point.setLongitude(110.0 + index * 0.001);
            point.setFrameCount((long) index);
            rows.add(point);
        }
        latest = rows.getLast();
        when(mapper.findCurrentState()).thenReturn(latest);
        when(mapper.findTrack("CA4732", latest.getSampleAt().minusHours(24), latest.getSampleAt()))
                .thenReturn(rows);

        var result = service.getCurrent();

        assertThat(result).isNotNull();
        assertThat(result.track()).hasSize(720);
        assertThat(result.track().getFirst().sampleAt()).isEqualTo(rows.getFirst().getSampleAt());
        assertThat(result.latestPoint().sampleAt()).isEqualTo(rows.getLast().getSampleAt());
    }

    @Test
    void startsTrackAtLatestSimulatorRunBoundary() {
        FlightTrackPointRow latest = row(now, "470");
        OffsetDateTime segmentStart = now.minusMinutes(20);
        when(mapper.findCurrentState()).thenReturn(latest);
        when(mapper.findCurrentSegmentStart("CA4732", now.minusHours(24), now))
                .thenReturn(segmentStart);
        when(mapper.findTrack("CA4732", segmentStart, now)).thenReturn(List.of(latest));

        var result = service.getCurrent();

        assertThat(result).isNotNull();
        assertThat(result.track()).hasSize(1);
    }

    private FlightTrackPointRow row(OffsetDateTime sampleAt, String groundSpeed) {
        FlightTrackPointRow row = new FlightTrackPointRow();
        row.setRecordId(UUID.randomUUID());
        row.setSampleAt(sampleAt);
        row.setSourceTimeText(sampleAt.toLocalTime().withNano(0).toString());
        row.setFlightNo("CA4732");
        row.setOrigin("ZBAA");
        row.setDestination("ZSHC");
        row.setAircraftRegistrationNo("B-TEST-001");
        row.setAircraftModel("Boeing 777-300ER");
        row.setAirlineCode("CA");
        row.setAltitudeFt(new BigDecimal("35000"));
        row.setComputedAirSpeedKt(new BigDecimal("285"));
        row.setGroundSpeedKt(new BigDecimal(groundSpeed));
        row.setLatitude(36.411113024);
        row.setLongitude(120.09225375);
        row.setTrackAngleDeg(new BigDecimal("180.100"));
        row.setHeadingDeg(new BigDecimal("181.100"));
        row.setPitchDeg(new BigDecimal("1.20"));
        row.setRollDeg(new BigDecimal("0.30"));
        row.setDistanceToGoNm(new BigDecimal("180"));
        row.setDestinationEtaText("20:00.0");
        row.setFrameCount(42L);
        return row;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<FlightTrackMapper> provider(FlightTrackMapper mapper) {
        ObjectProvider<FlightTrackMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        return provider;
    }
}
