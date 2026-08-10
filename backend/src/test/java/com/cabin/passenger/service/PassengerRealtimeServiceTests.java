package com.cabin.passenger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cabin.config.UdpProperties;
import com.cabin.passenger.entity.EntertainmentWorkRow;
import com.cabin.passenger.mapper.EntertainmentWorkMapper;
import com.cabin.udp.dto.CurrentFlightContext;
import com.cabin.udp.entity.DataRecord;
import com.cabin.udp.service.CurrentFlightContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class PassengerRealtimeServiceTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntertainmentWorkMapper workMapper = mock(EntertainmentWorkMapper.class);
    private final CurrentFlightContextService contextService =
            new CurrentFlightContextService(new UdpProperties(false, 0, 0, null, null, null, null));
    private final IfeCockrellCurrentStateCache stateCache = new IfeCockrellCurrentStateCache(objectMapper);
    private final PassengerRealtimeService service = new PassengerRealtimeService(
            provider(workMapper), contextService, stateCache
    );

    @Test
    void returnsAllSeatsWithEmptyBehaviorWhenNoCurrentKkreEventExists() {
        var result = service.getSnapshot();

        assertThat(result.hasData()).isFalse();
        assertThat(result.passengerActivities().items()).hasSize(282)
                .allMatch(item -> item.behaviorType() == null && item.activityKind() == null);
    }

    @Test
    void readsOnlyCurrentQarSessionCockrellState() {
        OffsetDateTime eventAt = OffsetDateTime.parse("2026-07-07T10:00:00+08:00");
        CurrentFlightContext context = establishSession(eventAt);
        when(workMapper.findEnabledWorks()).thenReturn(List.of(work()));

        stateCache.update(context, Map.of(
                "recordId", UUID.randomUUID(),
                "flightNo", "CA1234",
                "pnr", "ABC123",
                "seatNo", "A11",
                "cabinClass", "BUSINESS",
                "deviceId", "DEV-001",
                "passengerId", "PAX-00001",
                "behaviorType", "MOVIE_PLAY",
                "behaviorDetail", """
                        {"contentId":"MOV-001-2026","contentName":"星海远航","contentType":"科幻/传奇","playAction":"PAUSE"}
                        """,
                "eventAt", eventAt
        ), eventAt.plusSeconds(1));

        var result = service.getSnapshot();
        var activity = result.passengerActivities().items().getFirst();

        assertThat(result.hasData()).isTrue();
        assertThat(activity.seatNo()).isEqualTo("A11");
        assertThat(activity.activityKind()).isEqualTo("VIDEO");
        assertThat(activity.action()).isEqualTo("PAUSE");
        assertThat(activity.mediaWork().workCode()).isEqualTo("MOV-001-2026");
    }

    @Test
    void mapsKnownShoppingBehaviorToShopping() {
        OffsetDateTime eventAt = OffsetDateTime.parse("2026-07-07T10:00:00+08:00");
        CurrentFlightContext context = establishSession(eventAt);
        when(workMapper.findEnabledWorks()).thenReturn(List.of());

        stateCache.update(context, Map.of(
                "recordId", UUID.randomUUID(),
                "flightNo", "CA1234",
                "pnr", "ABC123",
                "seatNo", "A11",
                "cabinClass", "BUSINESS",
                "deviceId", "DEV-001",
                "passengerId", "PAX-00001",
                "behaviorType", "SHOPPING",
                "behaviorDetail", "{\"orderList\":[]}",
                "eventAt", eventAt
        ), eventAt.plusSeconds(1));

        var activity = service.getSnapshot().passengerActivities().items().getFirst();

        assertThat(activity.behaviorType()).isEqualTo("SHOPPING");
        assertThat(activity.activityKind()).isEqualTo("SHOPPING");
    }

    private CurrentFlightContext establishSession(OffsetDateTime startedAt) {
        DataRecord record = new DataRecord();
        record.setDataTypeCode("QAR");
        record.setFlightNo("CA1234");
        record.setOrigin("ZBAA");
        record.setDestination("ZSPD");
        record.setAirlineCode("CA");
        record.setReceivedAt(startedAt);
        return contextService.updateFromQar(record, UUID.randomUUID(), startedAt);
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
    private ObjectProvider<EntertainmentWorkMapper> provider(EntertainmentWorkMapper value) {
        ObjectProvider<EntertainmentWorkMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
