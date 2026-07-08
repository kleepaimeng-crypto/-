package com.cabin.passenger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cabin.passenger.entity.SmartWindowRow;
import com.cabin.passenger.mapper.PassengerSmartWindowMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class PassengerSmartWindowServiceTests {
    private final PassengerSmartWindowMapper mapper = mock(PassengerSmartWindowMapper.class);
    private final PassengerSmartWindowService service = new PassengerSmartWindowService(provider(mapper));

    @Test
    void returnsEmptySnapshotWhenNoCompleteRecordExists() {
        when(mapper.findLatestSnapshotRecordId()).thenReturn(null);

        var result = service.getLatestSnapshot();

        assertThat(result.hasData()).isFalse();
        assertThat(result.sourceRecordId()).isNull();
        assertThat(result.windows()).isEmpty();
        assertThat(result.complete()).isFalse();
        assertThat(result.actualCount()).isZero();
        assertThat(result.missingWindowIds()).hasSize(116);
        assertThat(result.summary().averageBrightness()).isNull();
    }

    @Test
    void returnsAllIdsMissingWhenLatestRecordHasNoParsedRows() {
        UUID recordId = UUID.randomUUID();
        when(mapper.findLatestSnapshotRecordId()).thenReturn(recordId);
        when(mapper.findSnapshotWindows(recordId)).thenReturn(List.of());

        var result = service.getLatestSnapshot();

        assertThat(result.hasData()).isFalse();
        assertThat(result.complete()).isFalse();
        assertThat(result.sourceRecordId()).isEqualTo(recordId);
        assertThat(result.actualCount()).isZero();
        assertThat(result.missingWindowIds()).containsExactlyElementsOf(
                java.util.stream.IntStream.rangeClosed(1, 116).boxed().toList()
        );
    }

    @Test
    void assemblesCompleteSnapshotAndSummary() {
        UUID recordId = UUID.randomUUID();
        List<SmartWindowRow> rows = completeRows();
        rows.get(0).setConnected(false);
        rows.get(1).setStatus("FAULT");
        rows.get(2).setStatus("TEST");
        when(mapper.findLatestSnapshotRecordId()).thenReturn(recordId);
        when(mapper.findSnapshotWindows(recordId)).thenReturn(rows);

        var result = service.getLatestSnapshot();

        assertThat(result.hasData()).isTrue();
        assertThat(result.complete()).isTrue();
        assertThat(result.expectedCount()).isEqualTo(116);
        assertThat(result.actualCount()).isEqualTo(116);
        assertThat(result.missingWindowIds()).isEmpty();
        assertThat(result.sourceRecordId()).isEqualTo(recordId);
        assertThat(result.windows()).hasSize(116);
        assertThat(result.windows()).extracting(item -> item.windowId())
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 116).boxed().toList());
        assertThat(result.summary().averageBrightness()).isEqualByComparingTo(new BigDecimal("5.0"));
        assertThat(result.summary().disconnectedCount()).isEqualTo(1);
        assertThat(result.summary().faultCount()).isEqualTo(1);
        assertThat(result.summary().testCount()).isEqualTo(1);
    }

    @Test
    void returnsAvailableRowsAndMissingIdsForPartialSnapshot() {
        UUID recordId = UUID.randomUUID();
        List<SmartWindowRow> rows = completeRows();
        rows.removeIf(row -> row.getWindowId() == 17 || row.getWindowId() == 68);
        when(mapper.findLatestSnapshotRecordId()).thenReturn(recordId);
        when(mapper.findSnapshotWindows(recordId)).thenReturn(rows);

        var result = service.getLatestSnapshot();

        assertThat(result.hasData()).isTrue();
        assertThat(result.complete()).isFalse();
        assertThat(result.actualCount()).isEqualTo(114);
        assertThat(result.windows()).hasSize(114);
        assertThat(result.missingWindowIds()).containsExactly(17, 68);
        assertThat(result.summary().averageBrightness()).isEqualByComparingTo(new BigDecimal("5.0"));
    }

    @Test
    void returnsOneAvailableWindowAndMarksTheRestMissing() {
        UUID recordId = UUID.randomUUID();
        SmartWindowRow row = completeRows().getFirst();
        when(mapper.findLatestSnapshotRecordId()).thenReturn(recordId);
        when(mapper.findSnapshotWindows(recordId)).thenReturn(List.of(row));

        var result = service.getLatestSnapshot();

        assertThat(result.hasData()).isTrue();
        assertThat(result.actualCount()).isEqualTo(1);
        assertThat(result.windows()).hasSize(1);
        assertThat(result.missingWindowIds()).hasSize(115).doesNotContain(1);
    }

    private List<SmartWindowRow> completeRows() {
        List<SmartWindowRow> rows = new ArrayList<>();
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-07-07T10:00:00+08:00");
        for (int windowId = 1; windowId <= 116; windowId++) {
            SmartWindowRow row = new SmartWindowRow();
            row.setWindowId(windowId);
            row.setZoneId(zoneId(windowId));
            row.setBrightnessLevel(5);
            row.setConnected(true);
            row.setStatus("NORMAL");
            row.setUpdatedAt(timestamp.plusSeconds(windowId));
            rows.add(row);
        }
        return rows;
    }

    private int zoneId(int windowId) {
        int sideSequence = windowId <= 58 ? windowId : windowId - 58;
        if (sideSequence <= 17) return 1;
        if (sideSequence <= 37) return 2;
        return 3;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<PassengerSmartWindowMapper> provider(PassengerSmartWindowMapper mapper) {
        ObjectProvider<PassengerSmartWindowMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        return provider;
    }
}
