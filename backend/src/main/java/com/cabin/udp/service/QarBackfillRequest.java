package com.cabin.udp.service;

import java.time.OffsetDateTime;
import java.util.UUID;

public record QarBackfillRequest(
        UUID flightSessionId,
        String flightNo,
        String origin,
        String destination,
        String airlineCode,
        OffsetDateTime applicationStartedAt
) {
}
