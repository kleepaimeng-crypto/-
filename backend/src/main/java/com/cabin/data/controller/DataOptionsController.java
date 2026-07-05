package com.cabin.data.controller;

import com.cabin.common.response.Response;
import com.cabin.common.trace.TraceContext;
import com.cabin.data.dto.DataOptionsResponse;
import com.cabin.data.service.DataOptionsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-options")
public class DataOptionsController {
    private final DataOptionsService dataOptionsService;

    public DataOptionsController(DataOptionsService dataOptionsService) {
        this.dataOptionsService = dataOptionsService;
    }

    @GetMapping
    public Response<DataOptionsResponse> options() {
        return Response.success(dataOptionsService.getOptions(), TraceContext.currentTraceId());
    }
}
