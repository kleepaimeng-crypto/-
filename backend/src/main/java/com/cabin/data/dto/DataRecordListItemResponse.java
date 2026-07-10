package com.cabin.data.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DataRecordListItemResponse(
        UUID id,
        String aircraftRegistrationNo,
        String aircraftModel,
        String airlineCode,
        String flightNo,
        String origin,
        String destination,
        CodeNameOption sourceDevice,
        CodeNameOption dataType,
        OffsetDateTime sentAt,
        OffsetDateTime receivedAt,
        int payloadCount,
        String parseStatus,
        List<TagResponse> tags,
        boolean deleted,
        int version
) {
}
