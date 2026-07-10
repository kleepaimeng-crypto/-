package com.cabin.log.controller;

import com.cabin.common.response.PageResponse;
import com.cabin.common.response.Response;
import com.cabin.common.trace.TraceContext;
import com.cabin.log.dto.AuditLogQuery;
import com.cabin.log.dto.AuditLogResponse;
import com.cabin.log.service.AuditLogQueryService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {
    private final AuditLogQueryService auditLogQueryService;

    public AuditLogController(AuditLogQueryService auditLogQueryService) {
        this.auditLogQueryService = auditLogQueryService;
    }

    @GetMapping
    public Response<PageResponse<AuditLogResponse>> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) UUID operatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime createdTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        AuditLogQuery query = new AuditLogQuery(
                action,
                targetType,
                targetId,
                operatorId,
                createdFrom,
                createdTo,
                page,
                pageSize
        );
        return Response.success(auditLogQueryService.listAuditLogs(query), TraceContext.currentTraceId());
    }
}
