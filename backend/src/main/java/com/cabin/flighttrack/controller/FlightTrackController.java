package com.cabin.flighttrack.controller;

import com.cabin.common.response.Response;
import com.cabin.common.trace.TraceContext;
import com.cabin.flighttrack.dto.FlightTrackCurrentResponse;
import com.cabin.flighttrack.service.FlightTrackService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flight-track")
public class FlightTrackController {
    private final FlightTrackService service;

    public FlightTrackController(FlightTrackService service) {
        this.service = service;
    }

    @GetMapping("/current")
    public Response<FlightTrackCurrentResponse> current() {
        return Response.success(service.getCurrent(), TraceContext.currentTraceId());
    }
}
