package com.cabin.data.dto;

import java.time.OffsetDateTime;

public record RecordMetadataResponse(
        String aircraftRegistrationNo,
        String aircraftModel,
        String airlineCode,
        String flightNo,
        String origin,
        String destination,
        String dataTypeCode,
        String sourceDeviceCode,
        String sourceSystemCode,
        OffsetDateTime sentAt,
        OffsetDateTime receivedAt,
        String parseStatus,
        String parseError,
        int version
) {
}
