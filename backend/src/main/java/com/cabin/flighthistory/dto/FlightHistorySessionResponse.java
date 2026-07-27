package com.cabin.flighthistory.dto;

import com.cabin.flighthistory.entity.FlightHistorySessionRow;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FlightHistorySessionResponse(
        UUID id, String flightNo, String origin, String destination, String aircraftRegistrationNo,
        String aircraftModel, String airlineCode, OffsetDateTime startedAt, OffsetDateTime endedAt,
        long durationSeconds, int pointCount, String finishReason, OffsetDateTime archivedAt
) {
    public static FlightHistorySessionResponse from(FlightHistorySessionRow row) {
        return new FlightHistorySessionResponse(
                row.getId(), row.getFlightNo(), row.getOrigin(), row.getDestination(),
                row.getAircraftRegistrationNo(), row.getAircraftModel(), row.getAirlineCode(),
                row.getStartedAt(), row.getEndedAt(), Duration.between(row.getStartedAt(), row.getEndedAt()).toSeconds(),
                row.getPointCount(), row.getFinishReason(), row.getArchivedAt()
        );
    }
}
