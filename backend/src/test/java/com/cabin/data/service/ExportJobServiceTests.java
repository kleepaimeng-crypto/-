package com.cabin.data.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cabin.common.security.CurrentUser;
import com.cabin.config.ExportProperties;
import com.cabin.data.entity.ExportRawPayloadRow;
import com.cabin.data.entity.OptionRow;
import com.cabin.data.mapper.DataRecordMapper;
import com.cabin.data.dto.ExportCreateRequest;
import com.cabin.data.dto.ExportGroupRequest;
import com.cabin.data.entity.ExportJobRow;
import com.cabin.data.mapper.ExportJobMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;

class ExportJobServiceTests {
    private final DataRecordMapper dataRecordMapper = mock(DataRecordMapper.class);
    private final ExportJobMapper exportJobMapper = mock(ExportJobMapper.class);

    @Test
    void createsCsvWithFlattenedColumns() throws Exception {
        Path storageDir = Files.createTempDirectory("cabin-export-test-");
        UUID userId = UUID.randomUUID();
        CurrentUser user = new CurrentUser(userId, "admin", null, "ADMIN");
        ExportJobRow job = new ExportJobRow();
        job.setId(UUID.randomUUID());
        job.setStatus("PENDING");
        job.setDataTypeCode("QAR");
        job.setFormat("CSV");

        OptionRow option = new OptionRow();
        option.setCode("QAR");
        option.setName("QAR 飞行数据");
        ExportRawPayloadRow row = new ExportRawPayloadRow();
        row.setId(UUID.randomUUID());
        row.setDataTypeCode("QAR");
        row.setRawPayload("{\"name\":\"测试,报文\",\"quote\":\"\\\"ok\\\"\"}");
        when(dataRecordMapper.findDataTypeOptions()).thenReturn(List.of(option));
        when(dataRecordMapper.findExportRawPayloadsByIds(any())).thenReturn(List.of(row));
        when(exportJobMapper.markRunning(any(), eq(userId))).thenReturn(1);
        when(exportJobMapper.findById(any(), eq(userId))).thenReturn(job);

        Executor directExecutor = Runnable::run;
        ExportJobService service = new ExportJobService(
                provider(dataRecordMapper),
                provider(exportJobMapper),
                new ObjectMapper().findAndRegisterModules(),
                new ExportProperties(storageDir.toString()),
                directExecutor
        );

        service.create(
                new ExportCreateRequest(
                        List.of(new ExportGroupRequest("QAR", List.of(row.getId())))
                ),
                user
        );

        Path csv = Files.list(storageDir).filter(path -> path.getFileName().toString().endsWith(".csv"))
                .findFirst()
                .orElseThrow();
        String content = Files.readString(csv);
        assertThat(content).contains("name,quote");
        assertThat(content).contains("\"测试,报文\",\"\"\"ok\"\"\"");
        verify(exportJobMapper).complete(
                any(), eq(userId), eq("SUCCEEDED"), anyString(), anyString(), anyLong(),
                eq(1), eq(1), eq(0)
        );

        Files.deleteIfExists(csv);
        Files.deleteIfExists(storageDir);
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
