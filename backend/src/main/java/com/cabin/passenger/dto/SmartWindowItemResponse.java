package com.cabin.passenger.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SmartWindowItemResponse(
        int windowId,
        int zoneId,
        int brightnessLevel,
        boolean connected,
        String status,
        OffsetDateTime updatedAt,
        UUID sourceRecordId
) {
}
