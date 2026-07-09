package com.cabin.flighttrack.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record FlightTrackCurrentResponse(
        FlightTrackInfoResponse flight,
        FlightTrackPointResponse latestPoint,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        int pollIntervalSeconds,
        int freshnessSeconds,
        List<FlightTrackPointResponse> track
) {
}
