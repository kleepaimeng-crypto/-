package com.cabin.data.controller;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.PageResponse;
import com.cabin.common.response.Response;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.security.CurrentUser;
import com.cabin.common.trace.TraceContext;
import com.cabin.data.dto.ExportCreateRequest;
import com.cabin.data.dto.ExportJobResponse;
import com.cabin.data.service.ExportJobService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {
    private final ExportJobService exportJobService;

    public ExportController(ExportJobService exportJobService) {
        this.exportJobService = exportJobService;
    }

    @PostMapping
    public Response<List<ExportJobResponse>> create(
            @Valid @RequestBody ExportCreateRequest request,
            Authentication authentication
    ) {
        return Response.success(
                exportJobService.create(request, currentUser(authentication)),
                TraceContext.currentTraceId()
        );
    }

    @GetMapping
    public Response<PageResponse<ExportJobResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            Authentication authentication
    ) {
        return Response.success(
                exportJobService.list(page, pageSize, currentUser(authentication)),
                TraceContext.currentTraceId()
        );
    }

    @GetMapping("/{jobId}")
    public Response<ExportJobResponse> get(@PathVariable UUID jobId, Authentication authentication) {
        return Response.success(
                exportJobService.get(jobId, currentUser(authentication)),
                TraceContext.currentTraceId()
        );
    }

    @DeleteMapping("/{jobId}")
    public Response<Void> delete(@PathVariable UUID jobId, Authentication authentication) {
        exportJobService.delete(jobId, currentUser(authentication));
        return Response.success(null, TraceContext.currentTraceId());
    }

    @GetMapping("/{jobId}/file")
    public ResponseEntity<FileSystemResource> download(@PathVariable UUID jobId, Authentication authentication) {
        ExportJobService.DownloadFile file = exportJobService.download(jobId, currentUser(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString()
                )
                .contentLength(file.path().toFile().length())
                .body(new FileSystemResource(file.path()));
    }

    private CurrentUser currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED, "缺少、过期或无效 JWT");
        }
        return currentUser;
    }
}
