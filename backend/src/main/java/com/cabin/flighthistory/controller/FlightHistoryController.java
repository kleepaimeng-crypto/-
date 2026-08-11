package com.cabin.flighthistory.controller;

import com.cabin.common.response.PageResponse;
import com.cabin.common.response.Response;
import com.cabin.common.trace.TraceContext;
import com.cabin.flighthistory.dto.FlightHistorySessionResponse;
import com.cabin.flighthistory.dto.FlightHistoryTrackResponse;
import com.cabin.flighthistory.service.FlightHistoryQueryService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@ConditionalOnProperty(name = "cabin.flight-history.enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v1/flight-history")
public class FlightHistoryController {
    private final FlightHistoryQueryService service;
    public FlightHistoryController(FlightHistoryQueryService service) { this.service = service; }

    @GetMapping("/sessions")
    public Response<PageResponse<FlightHistorySessionResponse>> list(
            @RequestParam(required = false) OffsetDateTime endedFrom, @RequestParam(required = false) OffsetDateTime endedTo,
            @RequestParam(required = false) String flightNo, @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination, @RequestParam(required = false) String aircraftRegistrationNo,
            @RequestParam(required = false) String finishReason, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, @RequestParam(defaultValue = "endedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection
    ) {
        return Response.success(service.list(endedFrom, endedTo, flightNo, origin, destination, aircraftRegistrationNo,
                finishReason, page, pageSize, sortBy, sortDirection), TraceContext.currentTraceId());
    }

    @GetMapping("/sessions/{sessionId}")
    public Response<FlightHistorySessionResponse> get(@PathVariable UUID sessionId) {
        return Response.success(service.get(sessionId), TraceContext.currentTraceId());
    }

    @GetMapping("/sessions/{sessionId}/track")
    public Response<FlightHistoryTrackResponse> track(
            @PathVariable UUID sessionId, @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to, @RequestParam(defaultValue = "3600") int maxPoints
    ) {
        return Response.success(service.getTrack(sessionId, from, to, maxPoints), TraceContext.currentTraceId());
    }
}
