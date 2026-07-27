package com.cabin.flighthistory.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record FlightHistoryTrackResponse(
        FlightHistorySessionResponse session,
        OffsetDateTime rangeStartAt,
        OffsetDateTime rangeEndAt,
        int sourcePointCount,
        int returnedPointCount,
        boolean sampled,
        List<FlightHistoryPointResponse> track
) { }
