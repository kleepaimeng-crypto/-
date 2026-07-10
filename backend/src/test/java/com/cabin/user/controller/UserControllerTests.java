package com.cabin.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cabin.common.exception.GlobalExceptionHandler;
import com.cabin.common.security.CurrentUser;
import com.cabin.user.dto.UserSummaryResponse;
import com.cabin.user.service.UserService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserControllerTests {
    private final UserService userService = mock(UserService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new UserController(userService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void createReturns201WithoutPasswordFields() throws Exception {
        UUID userId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID(), "root", null, "SUPER_ADMIN");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(currentUser, null);
        when(userService.createUser(any(), any(), any())).thenReturn(new UserSummaryResponse(
                userId,
                "operator",
                "operator@example.com",
                "USER",
                "PENDING",
                null,
                1,
                OffsetDateTime.parse("2026-07-09T10:00:00+08:00"),
                OffsetDateTime.parse("2026-07-09T10:00:00+08:00"),
                null
        ));

        mockMvc.perform(post("/api/v1/users")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"operator",
                                  "email":"operator@example.com",
                                  "password":"abc123",
                                  "roleCode":"USER",
                                  "status":"PENDING"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void createReturnsSpecificPasswordValidationMessage() throws Exception {
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID(), "root", null, "SUPER_ADMIN");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(currentUser, null);

        mockMvc.perform(post("/api/v1/users")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"operator",
                                  "email":"operator@example.com",
                                  "password":"abc12",
                                  "roleCode":"USER",
                                  "status":"PENDING"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("password"))
                .andExpect(jsonPath("$.details[0].reason").value("初始密码长度必须为 6–72 个字符"));
    }
}
