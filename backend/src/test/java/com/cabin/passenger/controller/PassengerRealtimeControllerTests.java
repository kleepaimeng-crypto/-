package com.cabin.passenger.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cabin.common.exception.GlobalExceptionHandler;
import com.cabin.passenger.dto.EntertainmentRecommendationResponse;
import com.cabin.passenger.dto.EntertainmentWorkResponse;
import com.cabin.passenger.dto.MediaRankResponse;
import com.cabin.passenger.dto.MediaStatisticsResponse;
import com.cabin.passenger.dto.PassengerActivitiesResponse;
import com.cabin.passenger.dto.PassengerActivityResponse;
import com.cabin.passenger.dto.PassengerRealtimeSnapshotResponse;
import com.cabin.passenger.service.PassengerRealtimeService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PassengerRealtimeControllerTests {
    private final PassengerRealtimeService service = mock(PassengerRealtimeService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new PassengerRealtimeController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void returnsUnifiedSnapshotEnvelope() throws Exception {
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-07-07T10:00:00+08:00");
        PassengerActivityResponse activity = new PassengerActivityResponse(
                "PAX-00001", "A11", "BUSINESS", "MOVIE_PLAY", "VIDEO",
                "星海远航", List.of("奇幻"), "PLAY", null, null, null,
                null, null, updatedAt, null, null,
                new EntertainmentWorkResponse(
                        "MOV-001-2026", "VIDEO", "星海远航", List.of("奇幻"),
                        "一段发生在深空中的远航故事。", "林澈", "星海纪事",
                        7680, 2026, "中文", "中国"
                ),
                List.of(new EntertainmentRecommendationResponse(
                        "MOV-016-2026", "VIDEO", "星门旧梦", List.of("奇幻"),
                        "黎川", 12, "SAME_TYPE"
                ))
        );
        when(service.getSnapshot()).thenReturn(new PassengerRealtimeSnapshotResponse(
                true,
                updatedAt,
                new MediaStatisticsResponse(1, List.of(new MediaRankResponse("奇幻", 1)), 0, List.of()),
                new PassengerActivitiesResponse(282, List.of(activity))
        ));

        mockMvc.perform(get("/api/v1/passenger-realtime/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.mediaStatistics.videoRanking[0].count").value(1))
                .andExpect(jsonPath("$.data.passengerActivities.total").value(282))
                .andExpect(jsonPath("$.data.passengerActivities.items[0].seatNo").value("A11"))
                .andExpect(jsonPath("$.data.passengerActivities.items[0].mediaWork.workCode")
                        .value("MOV-001-2026"))
                .andExpect(jsonPath("$.data.passengerActivities.items[0].recommendations[0].currentViewerCount")
                        .value(12))
                .andExpect(jsonPath("$.data.passengerActivities.items[0].recommendations[0].reason")
                        .value("SAME_TYPE"));
    }
}
