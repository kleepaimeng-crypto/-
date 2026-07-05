package com.cabin.data.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cabin.common.exception.GlobalExceptionHandler;
import com.cabin.common.security.CurrentUser;
import com.cabin.data.dto.AnnotationResponse;
import com.cabin.data.service.AnnotationService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AnnotationControllerTests {
    private final AnnotationService annotationService = mock(AnnotationService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AnnotationController(annotationService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void updateAnnotationReturnsUpdatedAnnotation() throws Exception {
        UUID annotationId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID(), "admin", null, "ADMIN");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(currentUser, null);
        when(annotationService.updateAnnotation(any(), any(), any(), any()))
                .thenReturn(new AnnotationResponse(
                        annotationId,
                        "修订后的批注",
                        OffsetDateTime.parse("2026-07-04T12:00:00+08:00"),
                        OffsetDateTime.parse("2026-07-04T12:01:00+08:00"),
                        2,
                        false
                ));

        mockMvc.perform(patch("/api/v1/annotations/{annotationId}", annotationId)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"修订后的批注","expectedVersion":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.deleted").value(false));
    }
}
