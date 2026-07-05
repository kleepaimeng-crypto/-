package com.cabin.data.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cabin.common.exception.GlobalExceptionHandler;
import com.cabin.data.dto.BatchDeleteResponse;
import com.cabin.common.response.PageResponse;
import com.cabin.common.security.CurrentUser;
import com.cabin.data.dto.DataRecordListItemResponse;
import com.cabin.data.dto.DataRecordQuery;
import com.cabin.data.dto.RecordMetadataResponse;
import com.cabin.data.service.DataRecordLifecycleService;
import com.cabin.data.service.DataRecordService;
import com.cabin.data.service.TagService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DataRecordControllerTests {
    private final DataRecordService service = mock(DataRecordService.class);
    private final DataRecordLifecycleService lifecycleService = mock(DataRecordLifecycleService.class);
    private final TagService tagService = mock(TagService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new DataRecordController(service, lifecycleService, tagService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void listParsesCommaSeparatedTagIdsAndReturnsEnvelope() throws Exception {
        UUID tagId = UUID.randomUUID();
        when(service.listRecords(any(DataRecordQuery.class)))
                .thenReturn(PageResponse.of(List.<DataRecordListItemResponse>of(), 1, 20, 0));

        mockMvc.perform(get("/api/v1/data-records")
                        .param("tagIds", tagId.toString())
                        .param("sortBy", "sentAt")
                        .param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.page").value(1));

        ArgumentCaptor<DataRecordQuery> captor = ArgumentCaptor.forClass(DataRecordQuery.class);
        org.mockito.Mockito.verify(service).listRecords(captor.capture());
        assertThat(captor.getValue().getTagIds()).containsExactly(tagId);
        assertThat(captor.getValue().getOrderBySql()).isEqualTo("r.sent_at");
        assertThat(captor.getValue().getSortDirectionSql()).isEqualTo("ASC");
    }

    @Test
    void listRejectsInvalidTagId() throws Exception {
        mockMvc.perform(get("/api/v1/data-records").param("tagIds", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("tagIds"));
    }

    @Test
    void updateMetadataReturnsUpdatedMetadata() throws Exception {
        UUID recordId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID(), "admin", null, "ADMIN");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(currentUser, null);
        when(service.updateMetadata(any(), any(), any(), any()))
                .thenReturn(new RecordMetadataResponse(
                        "B-TEST-001",
                        "Boeing 777-300ER",
                        "CA",
                        "CA4732",
                        "ZBAA",
                        "ZSPD",
                        "QAR",
                        "SIM-QAR",
                        "SIMULATOR",
                        OffsetDateTime.parse("2026-07-04T12:00:00+08:00"),
                        OffsetDateTime.parse("2026-07-04T12:00:01+08:00"),
                        "PARSED",
                        null,
                        2
                ));

        mockMvc.perform(patch("/api/v1/data-records/{recordId}/metadata", recordId)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aircraftRegistrationNo": "B-TEST-001",
                                  "aircraftModel": "Boeing 777-300ER",
                                  "airlineCode": "CA",
                                  "flightNo": "CA4732",
                                  "origin": "ZBAA",
                                  "destination": "ZSPD",
                                  "sourceDeviceCode": "SIM-QAR",
                                  "expectedVersion": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.sourceDeviceCode").value("SIM-QAR"));
    }

    @Test
    void deleteRecordReturnsNullEnvelope() throws Exception {
        UUID recordId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID(), "admin", null, "ADMIN");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(currentUser, null);

        mockMvc.perform(delete("/api/v1/data-records/{recordId}", recordId)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"测试清理","expectedVersion":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void batchDeleteReturnsSummary() throws Exception {
        UUID recordId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID(), "admin", null, "ADMIN");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(currentUser, null);
        when(lifecycleService.batchDeleteRecords(any(), any(), any()))
                .thenReturn(new BatchDeleteResponse(1, 1, 0));

        mockMvc.perform(post("/api/v1/data-records/batch-delete")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recordIds":["%s"],"reason":"批量清理"}
                                """.formatted(recordId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requested").value(1))
                .andExpect(jsonPath("$.data.deleted").value(1));
    }
}
