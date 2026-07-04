package com.cabin.login.controller;

import com.cabin.common.response.Response;
import com.cabin.common.trace.TraceContext;
import com.cabin.login.dto.LoginRequest;
import com.cabin.login.dto.LoginResponse;
import com.cabin.login.dto.UserInfoResponse;
import com.cabin.login.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Response<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return Response.success(
                authService.login(request, httpRequest.getRemoteAddr()),
                TraceContext.currentTraceId()
        );
    }

    @GetMapping("/me")
    public Response<UserInfoResponse> me(Authentication authentication) {
        return Response.success(authService.currentUser(authentication), TraceContext.currentTraceId());
    }
}
