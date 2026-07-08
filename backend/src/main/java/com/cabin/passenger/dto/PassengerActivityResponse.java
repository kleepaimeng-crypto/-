package com.cabin.passenger.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PassengerActivityResponse(
        String passengerId,
        String seatNo,
        String cabinClass,
        String behaviorType,
        String activityKind,
        String title,
        List<String> types,
        String action,
        String domain,
        String url,
        Long trafficBytes,
        BigDecimal bandwidthMbps,
        Long windowBytes,
        OffsetDateTime eventAt,
        OffsetDateTime bandwidthUpdatedAt,
        UUID sourceRecordId
) {
}
