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
import com.cabin.data.dto.AnnotationCreateRequest;
import com.cabin.data.dto.AnnotationResponse;
import com.cabin.data.dto.AnnotationUpdateRequest;
import com.cabin.data.entity.AnnotationRow;
import com.cabin.data.entity.DataRecordLifecycleRow;
import com.cabin.data.mapper.AnnotationMapper;
import com.cabin.data.mapper.DataRecordLifecycleMapper;
import com.cabin.log.service.AuditLogService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AnnotationServiceTests {
    private final AnnotationMapper annotationMapper = mock(AnnotationMapper.class);
    private final DataRecordLifecycleMapper lifecycleMapper = mock(DataRecordLifecycleMapper.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final AnnotationService service = new AnnotationService(
            provider(annotationMapper),
            lifecycleProvider(lifecycleMapper),
            auditLogService
    );

    @Test
    void createAnnotationRequiresActiveRecordAndWritesAudit() {
        UUID recordId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(lifecycleMapper.findLifecycle(recordId)).thenReturn(record(recordId, false));
        when(annotationMapper.insertAnnotation(any(), eq(recordId), eq("巡航验收"), eq(userId))).thenReturn(1);
        when(annotationMapper.findById(any(), eq(false))).thenAnswer(invocation ->
                annotation(invocation.getArgument(0), recordId, "巡航验收", false, 1));

        AnnotationResponse response = service.createAnnotation(
                recordId,
                new AnnotationCreateRequest(" 巡航验收 "),
                new CurrentUser(userId, "admin", null, "ADMIN"),
                "127.0.0.1"
        );

        assertThat(response.content()).isEqualTo("巡航验收");
        verify(auditLogService).recordSuccess(
                eq("CREATE_ANNOTATION"),
                eq("DATA_ANNOTATION"),
                eq(response.id().toString()),
                eq(userId),
                eq("127.0.0.1"),
                eq(null),
                any()
        );
    }

    @Test
    void updateAnnotationRejectsDeletedAnnotation() {
        UUID annotationId = UUID.randomUUID();
        when(annotationMapper.findById(annotationId, true))
                .thenReturn(annotation(annotationId, UUID.randomUUID(), "old", true, 1));

        assertThatThrownBy(() -> service.updateAnnotation(
                annotationId,
                new AnnotationUpdateRequest("new", 1),
                new CurrentUser(UUID.randomUUID(), "admin", null, "ADMIN"),
                "127.0.0.1"
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("已删除");
    }

    private DataRecordLifecycleRow record(UUID id, boolean deleted) {
        DataRecordLifecycleRow row = new DataRecordLifecycleRow();
        row.setId(id);
        row.setDeleted(deleted);
        row.setVersion(1);
        return row;
    }

    private AnnotationRow annotation(UUID id, UUID recordId, String content, boolean deleted, int version) {
        AnnotationRow row = new AnnotationRow();
        row.setId(id);
        row.setRecordId(recordId);
        row.setContent(content);
        row.setDeleted(deleted);
        row.setVersion(version);
        row.setCreatedAt(OffsetDateTime.parse("2026-07-04T12:00:00+08:00"));
        row.setUpdatedAt(OffsetDateTime.parse("2026-07-04T12:00:00+08:00"));
        return row;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<AnnotationMapper> provider(AnnotationMapper mapper) {
        ObjectProvider<AnnotationMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<DataRecordLifecycleMapper> lifecycleProvider(DataRecordLifecycleMapper mapper) {
        ObjectProvider<DataRecordLifecycleMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        return provider;
    }
}
