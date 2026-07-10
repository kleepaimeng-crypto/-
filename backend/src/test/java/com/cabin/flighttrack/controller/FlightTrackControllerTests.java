package com.cabin.flighttrack.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cabin.common.exception.GlobalExceptionHandler;
import com.cabin.flighttrack.dto.FlightTrackCurrentResponse;
import com.cabin.flighttrack.dto.FlightTrackInfoResponse;
import com.cabin.flighttrack.dto.FlightTrackPointResponse;
import com.cabin.flighttrack.service.FlightTrackService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FlightTrackControllerTests {
    private final FlightTrackService service = mock(FlightTrackService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new FlightTrackController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void returnsUnifiedCurrentEnvelope() throws Exception {
        OffsetDateTime sampleAt = OffsetDateTime.parse("2026-07-08T10:00:00+08:00");
        FlightTrackPointResponse point = new FlightTrackPointResponse(
                sampleAt,
                "10:00:00",
                42L,
                36.411113024,
                120.09225375,
                new BigDecimal("35000"),
                new BigDecimal("470"),
                new BigDecimal("285"),
                new BigDecimal("180.100"),
                new BigDecimal("181.100"),
                new BigDecimal("1.20"),
                new BigDecimal("0.30"),
                new BigDecimal("180"),
                "20:00.0"
        );
        when(service.getCurrent()).thenReturn(new FlightTrackCurrentResponse(
                new FlightTrackInfoResponse(
                        "B-TEST-001",
                        "Boeing 777-300ER",
                        "CA",
                        "中国国际航空",
                        "CA4732",
                        "ZBAA",
                        "北京首都国际机场",
                        "ZSHC",
                        "杭州萧山国际机场",
                        "飞行中",
                        sampleAt
                ),
                point,
                sampleAt,
                sampleAt,
                5,
                300,
                List.of(point)
        ));

        mockMvc.perform(get("/api/v1/flight-track/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.flight.flightNo").value("CA4732"))
                .andExpect(jsonPath("$.data.latestPoint.frameCount").value(42))
                .andExpect(jsonPath("$.data.track[0].latitude").value(36.411113024));
    }

    @Test
    void returnsNullDataWhenNoCurrentTrackExists() throws Exception {
        when(service.getCurrent()).thenReturn(null);

        mockMvc.perform(get("/api/v1/flight-track/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
