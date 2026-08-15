package com.cabin.passenger.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cabin.common.exception.GlobalExceptionHandler;
import com.cabin.passenger.dto.CockpitVideoConfigResponse;
import com.cabin.passenger.service.CockpitVideoService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CockpitVideoControllerTests {
    private final CockpitVideoService service = mock(CockpitVideoService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new CockpitVideoController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void returnsTheUnifiedCockpitVideoEnvelope() throws Exception {
        when(service.getConfig()).thenReturn(new CockpitVideoConfigResponse(
                true,
                "WEBRTC",
                "http://127.0.0.1:8889/cockpit"
        ));

        mockMvc.perform(get("/api/v1/passenger-realtime/cockpit-video"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.protocol").value("WEBRTC"))
                .andExpect(jsonPath("$.data.playbackUrl")
                        .value("http://127.0.0.1:8889/cockpit"));
    }

    @Test
    void omitsThePlaybackUrlWhenDisabled() throws Exception {
        when(service.getConfig()).thenReturn(new CockpitVideoConfigResponse(false, "WEBRTC", null));

        mockMvc.perform(get("/api/v1/passenger-realtime/cockpit-video"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.playbackUrl").doesNotExist());
    }
}
