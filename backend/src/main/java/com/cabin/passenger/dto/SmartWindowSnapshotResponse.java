package com.cabin.passenger.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SmartWindowSnapshotResponse(
        boolean hasData,
        boolean complete,
        int expectedCount,
        int actualCount,
        List<Integer> missingWindowIds,
        UUID sourceRecordId,
        OffsetDateTime updatedAt,
        SmartWindowSummaryResponse summary,
        List<SmartWindowItemResponse> windows
) {
}
