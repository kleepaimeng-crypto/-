package com.cabin.data.controller;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.Response;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.security.CurrentUser;
import com.cabin.common.trace.TraceContext;
import com.cabin.data.dto.AnnotationBatchCreateRequest;
import com.cabin.data.dto.AnnotationBatchCreateResponse;
import com.cabin.data.dto.AnnotationCreateRequest;
import com.cabin.data.dto.AnnotationDeleteRequest;
import com.cabin.data.dto.AnnotationResponse;
import com.cabin.data.dto.AnnotationUpdateRequest;
import com.cabin.data.service.AnnotationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AnnotationController {
    private final AnnotationService annotationService;

    public AnnotationController(AnnotationService annotationService) {
        this.annotationService = annotationService;
    }

    @GetMapping("/data-records/{recordId}/annotations")
    public Response<List<AnnotationResponse>> list(
            @PathVariable UUID recordId,
            @RequestParam(defaultValue = "false") boolean includeDeleted
    ) {
        return Response.success(
                annotationService.listAnnotations(recordId, includeDeleted),
                TraceContext.currentTraceId()
        );
    }

    @PostMapping("/data-records/{recordId}/annotations")
    public Response<AnnotationResponse> create(
            @PathVariable UUID recordId,
            @Valid @RequestBody AnnotationCreateRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        return Response.success(
                annotationService.createAnnotation(
                        recordId,
                        request,
                        currentUser(authentication),
                        httpRequest.getRemoteAddr()
                ),
                TraceContext.currentTraceId()
        );
    }

    @PostMapping("/data-records/annotations/batch")
    public Response<AnnotationBatchCreateResponse> batchCreate(
            @Valid @RequestBody AnnotationBatchCreateRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        return Response.success(
                annotationService.createAnnotations(
                        request,
                        currentUser(authentication),
                        httpRequest.getRemoteAddr()
                ),
                TraceContext.currentTraceId()
        );
    }

    @PatchMapping("/annotations/{annotationId}")
    public Response<AnnotationResponse> update(
            @PathVariable UUID annotationId,
            @Valid @RequestBody AnnotationUpdateRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        return Response.success(
                annotationService.updateAnnotation(
                        annotationId,
                        request,
                        currentUser(authentication),
                        httpRequest.getRemoteAddr()
                ),
                TraceContext.currentTraceId()
        );
    }

    @DeleteMapping("/annotations/{annotationId}")
    public Response<Void> delete(
            @PathVariable UUID annotationId,
            @Valid @RequestBody AnnotationDeleteRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        annotationService.deleteAnnotation(
                annotationId,
                request,
                currentUser(authentication),
                httpRequest.getRemoteAddr()
        );
        return Response.success(null, TraceContext.currentTraceId());
    }

    private CurrentUser currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED, "缺少、过期或无效 JWT");
        }
        return currentUser;
    }
}
