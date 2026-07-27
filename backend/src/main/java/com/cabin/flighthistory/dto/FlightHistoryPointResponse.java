package com.cabin.flighthistory.dto;

import com.cabin.flighthistory.entity.FlightHistoryPointRow;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FlightHistoryPointResponse(
        OffsetDateTime sampleAt, String sampleTimeText, long frameCount, String airGroundStatus,
        Double latitude, Double longitude, BigDecimal altitudeFt, BigDecimal groundSpeedKt,
        BigDecimal computedAirSpeedKt, BigDecimal trackAngleDeg, BigDecimal headingDeg,
        BigDecimal pitchDeg, BigDecimal rollDeg, BigDecimal distanceToGoNm, String destinationEtaText
) {
    public static FlightHistoryPointResponse from(FlightHistoryPointRow row) {
        return new FlightHistoryPointResponse(
                row.getSampleAt(), row.getSourceTimeText(), row.getFrameCount(), row.getAirGroundStatus(),
                row.getLatitude(), row.getLongitude(), row.getAltitudeFt(), row.getGroundSpeedKt(),
                row.getComputedAirSpeedKt(), row.getTrackAngleDeg(), row.getHeadingDeg(), row.getPitchDeg(),
                row.getRollDeg(), row.getDistanceToGoNm(), row.getDestinationEtaText()
        );
    }
}
