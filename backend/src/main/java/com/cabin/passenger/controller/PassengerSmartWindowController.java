package com.cabin.passenger.controller;

import com.cabin.common.response.Response;
import com.cabin.common.trace.TraceContext;
import com.cabin.passenger.dto.SmartWindowSnapshotResponse;
import com.cabin.passenger.service.PassengerSmartWindowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/passenger-realtime")
public class PassengerSmartWindowController {
    private final PassengerSmartWindowService service;

    public PassengerSmartWindowController(PassengerSmartWindowService service) {
        this.service = service;
    }

    @GetMapping("/smart-windows")
    public Response<SmartWindowSnapshotResponse> smartWindows() {
        return Response.success(service.getLatestSnapshot(), TraceContext.currentTraceId());
    }
}
