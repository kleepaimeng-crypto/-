package com.cabin.user.controller;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.PageResponse;
import com.cabin.common.response.Response;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.security.CurrentUser;
import com.cabin.common.trace.TraceContext;
import com.cabin.user.dto.UserCreateRequest;
import com.cabin.user.dto.UserDeleteRequest;
import com.cabin.user.dto.UserSummaryResponse;
import com.cabin.user.dto.UserUpdateRequest;
import com.cabin.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Response<PageResponse<UserSummaryResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection
    ) {
        return Response.success(
                userService.listUsers(page, pageSize, sortBy, sortDirection),
                TraceContext.currentTraceId()
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response<UserSummaryResponse> create(
            @Valid @RequestBody UserCreateRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        return Response.success(
                userService.createUser(request, currentUser(authentication), httpRequest.getRemoteAddr()),
                TraceContext.currentTraceId()
        );
    }

    @PatchMapping("/{userId}")
    public Response<UserSummaryResponse> update(
            @PathVariable UUID userId,
            @Valid @RequestBody UserUpdateRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        return Response.success(
                userService.updateUser(
                        userId,
                        request,
                        currentUser(authentication),
                        httpRequest.getRemoteAddr()
                ),
                TraceContext.currentTraceId()
        );
    }

    @DeleteMapping("/{userId}")
    public Response<Void> delete(
            @PathVariable UUID userId,
            @Valid @RequestBody UserDeleteRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        userService.deleteUser(
                userId,
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
