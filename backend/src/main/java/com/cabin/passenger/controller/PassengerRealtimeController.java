package com.cabin.passenger.controller;

import com.cabin.common.response.Response;
import com.cabin.common.trace.TraceContext;
import com.cabin.passenger.dto.PassengerRealtimeSnapshotResponse;
import com.cabin.passenger.service.PassengerRealtimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/passenger-realtime")
public class PassengerRealtimeController {
    private final PassengerRealtimeService service;

    public PassengerRealtimeController(PassengerRealtimeService service) {
        this.service = service;
    }

    @GetMapping("/snapshot")
    public Response<PassengerRealtimeSnapshotResponse> snapshot() {
        return Response.success(service.getSnapshot(), TraceContext.currentTraceId());
    }
}
