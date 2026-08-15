package com.cabin.passenger.controller;

import com.cabin.common.response.Response;
import com.cabin.common.trace.TraceContext;
import com.cabin.passenger.dto.CockpitVideoConfigResponse;
import com.cabin.passenger.service.CockpitVideoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/passenger-realtime")
public class CockpitVideoController {
    private final CockpitVideoService service;

    public CockpitVideoController(CockpitVideoService service) {
        this.service = service;
    }

    @GetMapping("/cockpit-video")
    public Response<CockpitVideoConfigResponse> config() {
        return Response.success(service.getConfig(), TraceContext.currentTraceId());
    }
}
