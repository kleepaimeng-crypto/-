package com.cabin.data.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.security.CurrentUser;
import com.cabin.data.dto.DeleteRecordRequest;
import com.cabin.data.dto.RestoreRecordRequest;
import com.cabin.data.entity.DataRecordDetailRow;
import com.cabin.data.entity.DataRecordListRow;
import com.cabin.data.mapper.DataRecordLifecycleMapper;
import com.cabin.data.mapper.DataRecordMapper;
import com.cabin.log.service.AuditLogService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class DataRecordLifecycleServiceTests {
    private final DataRecordLifecycleMapper lifecycleMapper = mock(DataRecordLifecycleMapper.class);
    private final DataRecordMapper dataRecordMapper = mock(DataRecordMapper.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final DataRecordLifecycleService service = new DataRecordLifecycleService(
            lifecycleProvider(lifecycleMapper),
            dataRecordProvider(dataRecordMapper),
            auditLogService
    );

    @Test
    void deleteRecordUsesOptimisticLockAndAudits() {
        UUID recordId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(dataRecordMapper.findDetail(recordId, true))
                .thenReturn(detail(recordId, false, 1))
                .thenReturn(detail(recordId, true, 2));
        when(lifecycleMapper.softDeleteRecord(recordId, userId, "测试清理", 1)).thenReturn(1);

        service.deleteRecord(
                recordId,
                new DeleteRecordRequest(" 测试清理 ", 1),
                new CurrentUser(userId, "admin", null, "ADMIN"),
                "127.0.0.1"
        );

        verify(auditLogService).recordSuccess(
                eq("DELETE_RECORD"),
                eq("DATA_RECORD"),
                eq(recordId.toString()),
                eq(userId),
                eq("127.0.0.1"),
                any(),
                any()
        );
    }

    @Test
    void deleteRecordRejectsAlreadyDeletedRecord() {
        UUID recordId = UUID.randomUUID();
        when(dataRecordMapper.findDetail(recordId, true)).thenReturn(detail(recordId, true, 2));

        assertThatThrownBy(() -> service.deleteRecord(
                recordId,
                new DeleteRecordRequest("测试清理", 2),
                new CurrentUser(UUID.randomUUID(), "admin", null, "ADMIN"),
                "127.0.0.1"
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("已经软删除");
    }

    @Test
    void restoreRecordReturnsListItemAfterRestore() {
        UUID recordId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(dataRecordMapper.findDetail(recordId, true))
                .thenReturn(detail(recordId, true, 2))
                .thenReturn(detail(recordId, false, 3));
        when(lifecycleMapper.restoreRecord(recordId)).thenReturn(1);
        when(lifecycleMapper.findListRowById(recordId)).thenReturn(listRow(recordId));
        when(dataRecordMapper.findTagsForRecords(List.of(recordId))).thenReturn(List.of());

        var response = service.restoreRecord(
                recordId,
                new RestoreRecordRequest("误删恢复"),
                new CurrentUser(userId, "admin", null, "ADMIN"),
                "127.0.0.1"
        );

        assertThat(response.id()).isEqualTo(recordId);
        assertThat(response.deleted()).isFalse();
    }

    private DataRecordDetailRow detail(UUID recordId, boolean deleted, int version) {
        DataRecordDetailRow row = new DataRecordDetailRow();
        row.setId(recordId);
        row.setAircraftRegistrationNo("B-TEST-001");
        row.setDataTypeCode("QAR");
        row.setSourceDeviceCode("SIM-QAR");
        row.setFlightNo("CA4732");
        row.setDeleted(deleted);
        row.setVersion(version);
        return row;
    }

    private DataRecordListRow listRow(UUID recordId) {
        DataRecordListRow row = new DataRecordListRow();
        row.setId(recordId);
        row.setAircraftRegistrationNo("B-TEST-001");
        row.setSourceDeviceCode("SIM-QAR");
        row.setDataTypeCode("QAR");
        row.setDataTypeName("QAR 飞行数据");
        row.setSentAt(OffsetDateTime.parse("2026-07-04T12:00:00+08:00"));
        row.setReceivedAt(OffsetDateTime.parse("2026-07-04T12:00:01+08:00"));
        row.setPayloadCount(1);
        row.setParseStatus("PARSED");
        row.setVersion(3);
        return row;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<DataRecordLifecycleMapper> lifecycleProvider(DataRecordLifecycleMapper mapper) {
        ObjectProvider<DataRecordLifecycleMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<DataRecordMapper> dataRecordProvider(DataRecordMapper mapper) {
        ObjectProvider<DataRecordMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        return provider;
    }
}
