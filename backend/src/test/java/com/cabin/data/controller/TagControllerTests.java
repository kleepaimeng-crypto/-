package com.cabin.data.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cabin.common.exception.GlobalExceptionHandler;
import com.cabin.common.security.CurrentUser;
import com.cabin.data.dto.TagManagementResponse;
import com.cabin.data.service.TagService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TagControllerTests {
    private final TagService tagService = mock(TagService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TagController(tagService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void createTagReturnsManagementResponse() throws Exception {
        UUID tagId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID(), "admin", null, "ADMIN");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(currentUser, null);
        when(tagService.createTag(any(), any(), any()))
                .thenReturn(new TagManagementResponse(tagId, "巡航", "#409EFF", true, 1));

        mockMvc.perform(post("/api/v1/tags")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"巡航","color":"#409EFF"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(tagId.toString()))
                .andExpect(jsonPath("$.data.enabled").value(true));
    }
}
