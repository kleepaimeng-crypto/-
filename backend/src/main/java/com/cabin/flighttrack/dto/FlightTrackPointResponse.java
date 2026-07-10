package com.cabin.flighttrack.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FlightTrackPointResponse(
        OffsetDateTime sampleAt,
        String sampleTimeText,
        Long frameCount,
        Double latitude,
        Double longitude,
        BigDecimal altitudeFt,
        BigDecimal groundSpeedKt,
        BigDecimal computedAirSpeedKt,
        BigDecimal trackAngleDeg,
        BigDecimal headingDeg,
        BigDecimal pitchDeg,
        BigDecimal rollDeg,
        BigDecimal distanceToGoNm,
        String destinationEtaText
) {
}
