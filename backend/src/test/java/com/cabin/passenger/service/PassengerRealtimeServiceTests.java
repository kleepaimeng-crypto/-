package com.cabin.passenger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cabin.passenger.entity.PassengerActivityRow;
import com.cabin.passenger.mapper.PassengerRealtimeMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class PassengerRealtimeServiceTests {
    private final PassengerRealtimeMapper mapper = mock(PassengerRealtimeMapper.class);
    private final PassengerRealtimeService service = new PassengerRealtimeService(provider(mapper));

    @Test
    void returnsAllSeatsAsIdleWhenNoIfeDataExists() {
        when(mapper.findCurrentFlightNo()).thenReturn(null);

        var result = service.getSnapshot();

        assertThat(result.hasData()).isFalse();
        assertThat(result.updatedAt()).isNull();
        assertThat(result.passengerActivities().total()).isEqualTo(237);
        assertThat(result.passengerActivities().items()).hasSize(237);
        assertThat(result.passengerActivities().items().getFirst().seatNo()).isEqualTo("A11");
        assertThat(result.passengerActivities().items().getLast().seatNo()).isEqualTo("H57");
        assertThat(result.passengerActivities().items())
                .allMatch(item -> "IDLE".equals(item.activityKind()));
    }

    @Test
    void assemblesActivitiesBandwidthAndDeduplicatedRankings() {
        OffsetDateTime eventAt = OffsetDateTime.parse("2026-07-07T10:00:00+08:00");
        PassengerActivityRow activity = activity("A11", "MOVIE_PLAY", eventAt);
        when(mapper.findCurrentFlightNo()).thenReturn("CA1234");
        when(mapper.findLatestActivities("CA1234")).thenReturn(List.of(activity));
        when(mapper.findLatestMediaTypes("CA1234", "MOVIE_PLAY"))
                .thenReturn(List.of("奇幻/科幻/奇幻"));
        when(mapper.findLatestMediaTypes("CA1234", "MUSIC_PLAY"))
                .thenReturn(List.of("民谣/轻音乐"));

        var result = service.getSnapshot();

        assertThat(result.hasData()).isTrue();
        assertThat(result.updatedAt()).isEqualTo(eventAt);
        assertThat(result.mediaStatistics().videoTotalCount()).isEqualTo(2);
        assertThat(result.mediaStatistics().videoRanking())
                .extracting(item -> item.type())
                .containsExactly("奇幻", "科幻");
        assertThat(result.mediaStatistics().musicTotalCount()).isEqualTo(2);
        var first = result.passengerActivities().items().getFirst();
        assertThat(first.seatNo()).isEqualTo("A11");
        assertThat(first.activityKind()).isEqualTo("VIDEO");
        assertThat(first.bandwidthMbps()).isEqualByComparingTo("8.420");
        assertThat(first.windowBytes()).isEqualTo(5_262_500L);
    }

    private PassengerActivityRow activity(String seatNo, String behaviorType, OffsetDateTime eventAt) {
        PassengerActivityRow row = new PassengerActivityRow();
        row.setPassengerId("PAX-00001");
        row.setSeatNo(seatNo);
        row.setCabinClass("BUSINESS");
        row.setBehaviorType(behaviorType);
        row.setTitle("星海远航");
        row.setTypesText("奇幻/科幻");
        row.setAction("PLAY");
        row.setBandwidthMbps(new BigDecimal("8.420"));
        row.setWindowBytes(5_262_500L);
        row.setEventAt(eventAt);
        row.setBandwidthUpdatedAt(eventAt.plusSeconds(1));
        row.setSourceRecordId(UUID.randomUUID());
        return row;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<PassengerRealtimeMapper> provider(PassengerRealtimeMapper mapper) {
        ObjectProvider<PassengerRealtimeMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        return provider;
    }
}
