package com.cabin.log.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cabin.common.exception.GlobalExceptionHandler;
import com.cabin.common.response.PageResponse;
import com.cabin.log.dto.AuditLogQuery;
import com.cabin.log.dto.AuditLogResponse;
import com.cabin.log.service.AuditLogQueryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuditLogControllerTests {
    private final AuditLogQueryService service = mock(AuditLogQueryService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AuditLogController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void listParsesAuditLogQuery() throws Exception {
        UUID operatorId = UUID.randomUUID();
        when(service.listAuditLogs(any(AuditLogQuery.class)))
                .thenReturn(PageResponse.of(List.<AuditLogResponse>of(), 1, 20, 0));

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("action", "DELETE_RECORD")
                        .param("targetType", "DATA_RECORD")
                        .param("operatorId", operatorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1));

        ArgumentCaptor<AuditLogQuery> captor = ArgumentCaptor.forClass(AuditLogQuery.class);
        org.mockito.Mockito.verify(service).listAuditLogs(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("DELETE_RECORD");
        assertThat(captor.getValue().getOperatorId()).isEqualTo(operatorId);
    }
}
