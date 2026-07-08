package com.cabin.passenger.service;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ResponseCode;
import com.cabin.passenger.dto.SmartWindowItemResponse;
import com.cabin.passenger.dto.SmartWindowSnapshotResponse;
import com.cabin.passenger.dto.SmartWindowSummaryResponse;
import com.cabin.passenger.entity.SmartWindowRow;
import com.cabin.passenger.mapper.PassengerSmartWindowMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PassengerSmartWindowService {
    private static final int EXPECTED_WINDOW_COUNT = 116;

    private final ObjectProvider<PassengerSmartWindowMapper> mapperProvider;

    public PassengerSmartWindowService(ObjectProvider<PassengerSmartWindowMapper> mapperProvider) {
        this.mapperProvider = mapperProvider;
    }

    @Transactional(readOnly = true)
    public SmartWindowSnapshotResponse getLatestSnapshot() {
        PassengerSmartWindowMapper mapper = mapper();
        UUID recordId = mapper.findLatestSnapshotRecordId();
        if (recordId == null) {
            return emptySnapshot(null);
        }

        List<SmartWindowRow> rows = mapper.findSnapshotWindows(recordId);
        Set<Integer> actualIds = rows.stream().map(SmartWindowRow::getWindowId).collect(java.util.stream.Collectors.toSet());
        List<Integer> missingWindowIds = IntStream.rangeClosed(1, EXPECTED_WINDOW_COUNT)
                .filter(windowId -> !actualIds.contains(windowId))
                .boxed()
                .toList();

        OffsetDateTime updatedAt = rows.stream()
                .map(SmartWindowRow::getUpdatedAt)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
        BigDecimal averageBrightness = rows.isEmpty()
                ? null
                : BigDecimal.valueOf(rows.stream().mapToInt(SmartWindowRow::getBrightnessLevel).sum())
                        .divide(BigDecimal.valueOf(rows.size()), 1, RoundingMode.HALF_UP);
        SmartWindowSummaryResponse summary = new SmartWindowSummaryResponse(
                averageBrightness,
                (int) rows.stream().filter(row -> !row.isConnected()).count(),
                (int) rows.stream().filter(row -> "FAULT".equals(row.getStatus())).count(),
                (int) rows.stream().filter(row -> "TEST".equals(row.getStatus())).count()
        );
        List<SmartWindowItemResponse> windows = rows.stream()
                .map(row -> new SmartWindowItemResponse(
                        row.getWindowId(),
                        row.getZoneId(),
                        row.getBrightnessLevel(),
                        row.isConnected(),
                        row.getStatus(),
                        row.getUpdatedAt(),
                        recordId
                ))
                .toList();

        return new SmartWindowSnapshotResponse(
                !rows.isEmpty(),
                missingWindowIds.isEmpty(),
                EXPECTED_WINDOW_COUNT,
                rows.size(),
                missingWindowIds,
                recordId,
                updatedAt,
                summary,
                windows
        );
    }

    private SmartWindowSnapshotResponse emptySnapshot(UUID recordId) {
        return new SmartWindowSnapshotResponse(
                false,
                false,
                EXPECTED_WINDOW_COUNT,
                0,
                IntStream.rangeClosed(1, EXPECTED_WINDOW_COUNT).boxed().toList(),
                recordId,
                null,
                new SmartWindowSummaryResponse(null, 0, 0, 0),
                List.of()
        );
    }

    private PassengerSmartWindowMapper mapper() {
        PassengerSmartWindowMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
        }
        return mapper;
    }
}
