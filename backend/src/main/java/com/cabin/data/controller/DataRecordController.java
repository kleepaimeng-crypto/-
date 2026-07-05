package com.cabin.data.controller;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ErrorDetail;
import com.cabin.common.response.PageResponse;
import com.cabin.common.response.Response;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.security.CurrentUser;
import com.cabin.common.trace.TraceContext;
import com.cabin.data.dto.DataRecordDetailResponse;
import com.cabin.data.dto.DataRecordListItemResponse;
import com.cabin.data.dto.DataRecordQuery;
import com.cabin.data.dto.MetadataUpdateRequest;
import com.cabin.data.dto.RecordMetadataResponse;
import com.cabin.data.service.DataRecordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-records")
public class DataRecordController {
    private final DataRecordService dataRecordService;

    public DataRecordController(DataRecordService dataRecordService) {
        this.dataRecordService = dataRecordService;
    }

    @GetMapping
    public Response<PageResponse<DataRecordListItemResponse>> list(
            @RequestParam(required = false) String tagIds,
            @RequestParam(required = false) String airlineCode,
            @RequestParam(required = false) String flightNo,
            @RequestParam(required = false) String sourceDeviceCode,
            @RequestParam(required = false) String aircraftModel,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String dataTypeCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime receivedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime receivedTo,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "receivedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection
    ) {
        DataRecordQuery query = new DataRecordQuery(
                parseTagIds(tagIds),
                airlineCode,
                flightNo,
                sourceDeviceCode,
                aircraftModel,
                origin,
                destination,
                dataTypeCode,
                receivedFrom,
                receivedTo,
                includeDeleted,
                page,
                pageSize,
                sortBy,
                sortDirection
        );
        return Response.success(dataRecordService.listRecords(query), TraceContext.currentTraceId());
    }

    @GetMapping("/{recordId}")
    public Response<DataRecordDetailResponse> detail(
            @PathVariable UUID recordId,
            @RequestParam(defaultValue = "false") boolean includeDeleted
    ) {
        return Response.success(
                dataRecordService.getDetail(recordId, includeDeleted),
                TraceContext.currentTraceId()
        );
    }

    @PatchMapping("/{recordId}/metadata")
    public Response<RecordMetadataResponse> updateMetadata(
            @PathVariable UUID recordId,
            @Valid @RequestBody MetadataUpdateRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        return Response.success(
                dataRecordService.updateMetadata(
                        recordId,
                        request,
                        currentUser(authentication),
                        httpRequest.getRemoteAddr()
                ),
                TraceContext.currentTraceId()
        );
    }

    private List<UUID> parseTagIds(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        for (String part : rawValue.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(
                        ResponseCode.VALIDATION_ERROR,
                        "tagIds 格式非法",
                        List.of(new ErrorDetail("tagIds", "invalid_uuid"))
                );
            }
        }
        return ids;
    }

    private CurrentUser currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED, "缺少、过期或无效 JWT");
        }
        return currentUser;
    }
}
