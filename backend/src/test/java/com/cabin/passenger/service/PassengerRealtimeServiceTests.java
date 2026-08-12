package com.cabin.passenger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cabin.passenger.entity.EntertainmentWorkRow;
import com.cabin.passenger.entity.PassengerActivityRow;
import com.cabin.passenger.mapper.EntertainmentWorkMapper;
import com.cabin.passenger.mapper.PassengerRealtimeMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class PassengerRealtimeServiceTests {
    private static final UUID SESSION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final EntertainmentWorkMapper workMapper = mock(EntertainmentWorkMapper.class);
    private final PassengerRealtimeMapper realtimeMapper = mock(PassengerRealtimeMapper.class);
    private final PassengerRealtimeService service = new PassengerRealtimeService(
            provider(workMapper), provider(realtimeMapper)
    );

    @Test
    void returnsAllSeatsWithEmptyBehaviorWhenNoActiveFlightSessionExists() {
        when(realtimeMapper.findLatestActiveFlightSessionId()).thenReturn(null);

        var result = service.getSnapshot();

        assertThat(result.hasData()).isFalse();
        assertThat(result.passengerActivities().items()).hasSize(282)
                .allMatch(item -> item.behaviorType() == null && item.activityKind() == null);
        assertThat(result.passengerActivities().items().subList(0, 8))
                .allMatch(item -> "FIRST".equals(item.cabinClass()));
        assertThat(result.passengerActivities().items().get(8).cabinClass()).isEqualTo("BUSINESS");
    }

    @Test
    void readsLatestCockrellActivitiesFromCurrentDatabaseSession() {
        OffsetDateTime eventAt = OffsetDateTime.parse("2026-07-07T10:00:00+08:00");
        when(realtimeMapper.findLatestActiveFlightSessionId()).thenReturn(SESSION_ID);
        when(realtimeMapper.findLatestActivities(eq(SESSION_ID), anyList()))
                .thenReturn(List.of(activity("MOVIE_PLAY", eventAt)));
        when(workMapper.findEnabledWorks()).thenReturn(List.of(work()));

        var result = service.getSnapshot();
        var activity = result.passengerActivities().items().getFirst();

        assertThat(result.hasData()).isTrue();
        assertThat(activity.seatNo()).isEqualTo("A11");
        assertThat(activity.activityKind()).isEqualTo("VIDEO");
        assertThat(activity.action()).isEqualTo("PAUSE");
        assertThat(activity.mediaWork().workCode()).isEqualTo("MOV-001-2026");
        verify(realtimeMapper).findLatestActivities(eq(SESSION_ID), anyList());
    }

    @Test
    void mapsKnownShoppingBehaviorToShopping() {
        OffsetDateTime eventAt = OffsetDateTime.parse("2026-07-07T10:00:00+08:00");
        PassengerActivityRow row = activity("SHOPPING", eventAt);
        row.setMediaCode(null);
        row.setTitle(null);
        row.setTypesText(null);
        row.setAction(null);
        when(realtimeMapper.findLatestActiveFlightSessionId()).thenReturn(SESSION_ID);
        when(realtimeMapper.findLatestActivities(eq(SESSION_ID), anyList())).thenReturn(List.of(row));
        when(workMapper.findEnabledWorks()).thenReturn(List.of());

        var activity = service.getSnapshot().passengerActivities().items().getFirst();

        assertThat(activity.behaviorType()).isEqualTo("SHOPPING");
        assertThat(activity.activityKind()).isEqualTo("SHOPPING");
    }

    @Test
    void mapsCastScreenDetailsWithoutMediaRecommendations() {
        OffsetDateTime eventAt = OffsetDateTime.parse("2026-07-07T10:00:00+08:00");
        PassengerActivityRow row = activity("CAST_SCREEN", eventAt);
        row.setMediaCode(null);
        row.setTitle("SVDU-F01");
        row.setTypesText(null);
        row.setAction("CAST");
        row.setTargetDevice("SVDU-F01");
        row.setCastAction("CAST");
        row.setCastStatus("CONNECTED");
        row.setResolution("1080P");
        row.setCastDurationSeconds(600);
        when(realtimeMapper.findLatestActiveFlightSessionId()).thenReturn(SESSION_ID);
        when(realtimeMapper.findLatestActivities(eq(SESSION_ID), anyList())).thenReturn(List.of(row));
        when(workMapper.findEnabledWorks()).thenReturn(List.of());

        var activity = service.getSnapshot().passengerActivities().items().getFirst();

        assertThat(activity.activityKind()).isEqualTo("CAST_SCREEN");
        assertThat(activity.targetDevice()).isEqualTo("SVDU-F01");
        assertThat(activity.castStatus()).isEqualTo("CONNECTED");
        assertThat(activity.resolution()).isEqualTo("1080P");
        assertThat(activity.castDurationSeconds()).isEqualTo(600);
        assertThat(activity.recommendations()).isEmpty();
    }

    private PassengerActivityRow activity(String behaviorType, OffsetDateTime eventAt) {
        PassengerActivityRow row = new PassengerActivityRow();
        row.setPassengerId("PAX-00001");
        row.setSeatNo("A11");
        row.setCabinClass("BUSINESS");
        row.setBehaviorType(behaviorType);
        row.setMediaCode("MOV-001-2026");
        row.setTitle("星海远航");
        row.setTypesText("科幻/传奇");
        row.setAction("PAUSE");
        row.setEventAt(eventAt);
        row.setBandwidthUpdatedAt(eventAt.plusSeconds(1));
        row.setSourceRecordId(UUID.randomUUID());
        return row;
    }

    private EntertainmentWorkRow work() {
        EntertainmentWorkRow work = new EntertainmentWorkRow();
        work.setId(1L);
        work.setWorkCode("MOV-001-2026");
        work.setCategory("VIDEO");
        work.setTitle("星海远航");
        work.setSummary("作品简介");
        work.setCreatorName("测试导演");
        work.setCollectionName("测试系列");
        work.setDurationSeconds(7200);
        work.setReleaseYear(2026);
        work.setLanguage("中文");
        work.setRegion("中国");
        work.setSortOrder(1);
        work.setGenresText("科幻/传奇");
        return work;
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
