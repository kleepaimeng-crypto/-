package com.cabin.passenger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cabin.passenger.entity.EntertainmentWorkRow;
import com.cabin.passenger.entity.PassengerActivityRow;
import com.cabin.passenger.mapper.EntertainmentWorkMapper;
import com.cabin.passenger.mapper.PassengerRealtimeMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class PassengerRealtimeServiceTests {
    private final PassengerRealtimeMapper mapper = mock(PassengerRealtimeMapper.class);
    private final EntertainmentWorkMapper workMapper = mock(EntertainmentWorkMapper.class);
    private final PassengerRealtimeService service = new PassengerRealtimeService(
            provider(mapper),
            provider(workMapper)
    );

    @Test
    void returnsAllSeatsAsIdleWhenNoIfeDataExists() {
        when(mapper.findCurrentFlightNo()).thenReturn(null);

        var result = service.getSnapshot();

        assertThat(result.hasData()).isFalse();
        assertThat(result.updatedAt()).isNull();
        assertThat(result.passengerActivities().total()).isEqualTo(282);
        assertThat(result.passengerActivities().items()).hasSize(282);
        assertThat(result.passengerActivities().items().getFirst().seatNo()).isEqualTo("A11");
        assertThat(result.passengerActivities().items().getLast().seatNo()).isEqualTo("K58");
        assertThat(result.passengerActivities().items())
                .allMatch(item -> "IDLE".equals(item.activityKind())
                        && item.mediaWork() == null
                        && item.recommendations().isEmpty());
    }

    @Test
    void enrichesCatalogMetadataAndRanksSameTypeBeforePopularFallback() {
        OffsetDateTime eventAt = OffsetDateTime.parse("2026-07-07T10:00:00+08:00");
        EntertainmentWorkRow current = work(
                "MOV-001-2026", "VIDEO", "星海远航", "科幻/传奇", 1
        );
        EntertainmentWorkRow sameTypePopular = work(
                "MOV-005-2026", "VIDEO", "银河竞技场", "科幻/竞技", 5
        );
        EntertainmentWorkRow sameTypeSecond = work(
                "MOV-019-2026", "VIDEO", "深空来客", "科幻/猎奇", 19
        );
        EntertainmentWorkRow categoryPopular = work(
                "MOV-002-2026", "VIDEO", "云端恋曲", "爱情/都市", 2
        );
        EntertainmentWorkRow unused = work(
                "MOV-014-2026", "VIDEO", "夏日回声", "青春/爱情", 14
        );
        EntertainmentWorkRow musicWork = work(
                "MUS-002-2026", "MUSIC", "夜航民谣", "民谣/乡村", 2
        );

        PassengerActivityRow target = activity(
                "PAX-00001", "A11", "MOVIE_PLAY", "MOV-999-2026",
                "星海远航", "随机旧类型", eventAt
        );
        List<PassengerActivityRow> rows = List.of(
                target,
                activity("PAX-00002", "D11", "MOVIE_PLAY", "MOV-005-2026",
                        "银河竞技场", "错误类型", eventAt),
                activity("PAX-00003", "G11", "MOVIE_PLAY", "MOV-005-2026",
                        "银河竞技场", "错误类型", eventAt),
                activity("PAX-00004", "K11", "MOVIE_PLAY", "MOV-019-2026",
                        "深空来客", "错误类型", eventAt),
                activity("PAX-00005", "A12", "MOVIE_PLAY", "MOV-002-2026",
                        "云端恋曲", "错误类型", eventAt),
                activity("PAX-00006", "D12", "MOVIE_PLAY", "MOV-002-2026",
                        "云端恋曲", "错误类型", eventAt),
                activity("PAX-00007", "G12", "MOVIE_PLAY", "MOV-002-2026",
                        "云端恋曲", "错误类型", eventAt),
                activity("PAX-00008", "K12", "MUSIC_PLAY", "MUS-002-2026",
                        "夜航民谣", "错误类型", eventAt),
                activity("PAX-00009", "A13", "WAP_BROWSING", null,
                        "example.com", null, eventAt)
        );
        when(mapper.findCurrentFlightNo()).thenReturn("CA1234");
        when(mapper.findLatestActivities("CA1234")).thenReturn(rows);
        when(workMapper.findEnabledWorks()).thenReturn(List.of(
                current, sameTypePopular, sameTypeSecond, categoryPopular, unused, musicWork
        ));

        var result = service.getSnapshot();
        var first = result.passengerActivities().items().getFirst();

        assertThat(result.hasData()).isTrue();
        assertThat(result.updatedAt()).isEqualTo(eventAt);
        assertThat(first.seatNo()).isEqualTo("A11");
        assertThat(first.mediaWork()).isNotNull();
        assertThat(first.mediaWork().workCode()).isEqualTo("MOV-001-2026");
        assertThat(first.types()).containsExactly("科幻", "传奇");
        assertThat(first.mediaWork().summary()).isEqualTo("作品简介：星海远航");
        assertThat(first.bandwidthMbps()).isEqualByComparingTo("8.420");
        assertThat(first.recommendations())
                .extracting(item -> item.workCode())
                .containsExactly("MOV-005-2026", "MOV-019-2026", "MOV-002-2026");
        assertThat(first.recommendations())
                .extracting(item -> item.currentViewerCount())
                .containsExactly(2, 1, 3);
        assertThat(first.recommendations())
                .extracting(item -> item.reason())
                .containsExactly("SAME_TYPE", "SAME_TYPE", "CATEGORY_POPULAR");
        assertThat(first.recommendations())
                .noneMatch(item -> "MOV-001-2026".equals(item.workCode()));

        assertThat(result.mediaStatistics().videoTotalCount()).isEqualTo(7);
        assertThat(result.mediaStatistics().videoRanking())
                .extracting(item -> item.type())
                .doesNotContain("错误类型", "随机旧类型");
        assertThat(result.mediaStatistics().musicTotalCount()).isEqualTo(1);

        var browsing = result.passengerActivities().items().stream()
                .filter(item -> "A13".equals(item.seatNo()))
                .findFirst()
                .orElseThrow();
        assertThat(browsing.activityKind()).isEqualTo("BROWSING");
        assertThat(browsing.mediaWork()).isNull();
        assertThat(browsing.recommendations()).isEmpty();
    }

    @Test
    void usesStableOrderingAndSupportsUncataloguedMediaTypes() {
        OffsetDateTime eventAt = OffsetDateTime.parse("2026-07-07T10:00:00+08:00");
        PassengerActivityRow unknown = activity(
                "PAX-00001", "A11", "MOVIE_PLAY", "MOV-404-2026",
                "未收录影片", "科幻", eventAt
        );
        EntertainmentWorkRow first = work(
                "MOV-010-2026", "VIDEO", "十号作品", "科幻", 10
        );
        EntertainmentWorkRow second = work(
                "MOV-011-2026", "VIDEO", "十一号作品", "科幻", 11
        );
        EntertainmentWorkRow third = work(
                "MOV-012-2026", "VIDEO", "十二号作品", "科幻", 12
        );
        when(mapper.findCurrentFlightNo()).thenReturn("CA1234");
        when(mapper.findLatestActivities("CA1234")).thenReturn(List.of(unknown));
        when(workMapper.findEnabledWorks()).thenReturn(List.of(third, second, first));

        var result = service.getSnapshot();
        var activity = result.passengerActivities().items().getFirst();

        assertThat(activity.mediaWork()).isNull();
        assertThat(activity.title()).isEqualTo("未收录影片");
        assertThat(activity.types()).containsExactly("科幻");
        assertThat(activity.recommendations())
                .extracting(item -> item.workCode())
                .containsExactly("MOV-010-2026", "MOV-011-2026", "MOV-012-2026");
        assertThat(activity.recommendations())
                .allMatch(item -> "SAME_TYPE".equals(item.reason()));
    }

    private PassengerActivityRow activity(
            String passengerId,
            String seatNo,
            String behaviorType,
            String mediaCode,
            String title,
            String typesText,
            OffsetDateTime eventAt
    ) {
        PassengerActivityRow row = new PassengerActivityRow();
        row.setPassengerId(passengerId);
        row.setSeatNo(seatNo);
        row.setCabinClass("BUSINESS");
        row.setBehaviorType(behaviorType);
        row.setMediaCode(mediaCode);
        row.setTitle(title);
        row.setTypesText(typesText);
        row.setAction("PLAY");
        row.setDomain("example.com");
        row.setUrl("https://example.com");
        row.setTrafficBytes(1024L);
        row.setBandwidthMbps(new BigDecimal("8.420"));
        row.setWindowBytes(5_262_500L);
        row.setEventAt(eventAt);
        row.setBandwidthUpdatedAt(eventAt.plusSeconds(1));
        row.setSourceRecordId(UUID.randomUUID());
        return row;
    }

    private EntertainmentWorkRow work(
            String workCode,
            String category,
            String title,
            String genres,
            int sortOrder
    ) {
        EntertainmentWorkRow work = new EntertainmentWorkRow();
        work.setId((long) sortOrder);
        work.setWorkCode(workCode);
        work.setCategory(category);
        work.setTitle(title);
        work.setSummary("作品简介：" + title);
        work.setCreatorName(category.equals("VIDEO") ? "测试导演" : "测试音乐人");
        work.setCollectionName("测试系列");
        work.setDurationSeconds(category.equals("VIDEO") ? 7200 : 240);
        work.setReleaseYear(2026);
        work.setLanguage("中文");
        work.setRegion("中国");
        work.setSortOrder(sortOrder);
        work.setGenresText(genres);
        return work;
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
