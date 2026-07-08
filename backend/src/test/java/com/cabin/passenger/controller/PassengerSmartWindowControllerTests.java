package com.cabin.passenger.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cabin.common.exception.GlobalExceptionHandler;
import com.cabin.passenger.dto.SmartWindowItemResponse;
import com.cabin.passenger.dto.SmartWindowSnapshotResponse;
import com.cabin.passenger.dto.SmartWindowSummaryResponse;
import com.cabin.passenger.service.PassengerSmartWindowService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PassengerSmartWindowControllerTests {
    private final PassengerSmartWindowService service = mock(PassengerSmartWindowService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new PassengerSmartWindowController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void returnsUnifiedEnvelope() throws Exception {
        UUID recordId = UUID.randomUUID();
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-07-07T10:00:00+08:00");
        when(service.getLatestSnapshot()).thenReturn(new SmartWindowSnapshotResponse(
                true,
                recordId,
                updatedAt,
                new SmartWindowSummaryResponse(new BigDecimal("7.0"), 0, 0, 0),
                List.of(new SmartWindowItemResponse(1, 1, 7, true, "NORMAL", updatedAt, recordId))
        ));

        mockMvc.perform(get("/api/v1/passenger-realtime/smart-windows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.hasData").value(true))
                .andExpect(jsonPath("$.data.windows[0].windowId").value(1))
                .andExpect(jsonPath("$.data.windows[0].brightnessLevel").value(7));
    }
}
