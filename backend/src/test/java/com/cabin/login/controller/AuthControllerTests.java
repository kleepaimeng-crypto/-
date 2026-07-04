package com.cabin.login.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cabin.common.exception.GlobalExceptionHandler;
import com.cabin.common.security.CurrentUser;
import com.cabin.login.dto.LoginRequest;
import com.cabin.login.dto.LoginResponse;
import com.cabin.login.dto.UserInfoResponse;
import com.cabin.login.service.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTests {
    private final AuthService authService = mock(AuthService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AuthController(authService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void loginReturnsContractEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenReturn(new LoginResponse(
                        "jwt-token",
                        "Bearer",
                        7200,
                        new UserInfoResponse(userId, "admin", "admin@example.invalid", "ADMIN")
                ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.username").value("admin"));
    }

    @Test
    void meReturnsCurrentUserEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(userId, "admin", "admin@example.invalid", "ADMIN");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(currentUser, null);
        when(authService.currentUser(authentication))
                .thenReturn(new UserInfoResponse(userId, "admin", "admin@example.invalid", "ADMIN"));

        mockMvc.perform(get("/api/v1/auth/me").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.roleCode").value("ADMIN"));
    }
}
