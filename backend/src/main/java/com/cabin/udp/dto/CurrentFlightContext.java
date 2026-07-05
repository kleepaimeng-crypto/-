package com.cabin.udp.dto;

import java.time.OffsetDateTime;

public record CurrentFlightContext(
        String flightNo,
        String origin,
        String destination,
        String airlineCode,
        OffsetDateTime updatedAt
) {
    public boolean hasRoute() {
        return hasText(flightNo) && hasText(origin) && hasText(destination);
    }

    public boolean hasFlightNo() {
        return hasText(flightNo);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
