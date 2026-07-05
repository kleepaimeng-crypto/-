package com.cabin.data.controller;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.Response;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.security.CurrentUser;
import com.cabin.common.trace.TraceContext;
import com.cabin.data.dto.TagCreateRequest;
import com.cabin.data.dto.TagDeleteRequest;
import com.cabin.data.dto.TagManagementResponse;
import com.cabin.data.dto.TagUpdateRequest;
import com.cabin.data.service.TagService;
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
@RequestMapping("/api/v1/tags")
public class TagController {
    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public Response<List<TagManagementResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeDisabled
    ) {
        return Response.success(tagService.listTags(includeDisabled), TraceContext.currentTraceId());
    }

    @PostMapping
    public Response<TagManagementResponse> create(
            @Valid @RequestBody TagCreateRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        return Response.success(
                tagService.createTag(request, currentUser(authentication), httpRequest.getRemoteAddr()),
                TraceContext.currentTraceId()
        );
    }

    @PatchMapping("/{tagId}")
    public Response<TagManagementResponse> update(
            @PathVariable UUID tagId,
            @Valid @RequestBody TagUpdateRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        return Response.success(
                tagService.updateTag(tagId, request, currentUser(authentication), httpRequest.getRemoteAddr()),
                TraceContext.currentTraceId()
        );
    }

    @DeleteMapping("/{tagId}")
    public Response<Void> delete(
            @PathVariable UUID tagId,
            @Valid @RequestBody TagDeleteRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        tagService.deleteTag(tagId, request, currentUser(authentication), httpRequest.getRemoteAddr());
        return Response.success(null, TraceContext.currentTraceId());
    }

    private CurrentUser currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED, "缺少、过期或无效 JWT");
        }
        return currentUser;
    }
}
