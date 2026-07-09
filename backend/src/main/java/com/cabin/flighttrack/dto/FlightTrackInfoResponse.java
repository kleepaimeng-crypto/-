package com.cabin.flighttrack.dto;

import java.time.OffsetDateTime;

public record FlightTrackInfoResponse(
        String aircraftRegistrationNo,
        String aircraftModel,
        String airlineCode,
        String airlineName,
        String flightNo,
        String originAirportCode,
        String originAirportName,
        String destinationAirportCode,
        String destinationAirportName,
        String statusText,
        OffsetDateTime lastUpdatedAt
) {
}
