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
import com.cabin.data.dto.BatchTagRequest;
import com.cabin.data.dto.TagBatchMode;
import com.cabin.data.dto.TagCreateRequest;
import com.cabin.data.dto.TagManagementResponse;
import com.cabin.data.entity.TagRow;
import com.cabin.data.mapper.TagMapper;
import com.cabin.log.service.AuditLogService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;

class TagServiceTests {
    private final TagMapper mapper = mock(TagMapper.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final TagService service = new TagService(provider(mapper), auditLogService);

    @Test
    void createTagNormalizesColorAndWritesAudit() {
        UUID userId = UUID.randomUUID();
        when(mapper.insertTag(any())).thenReturn(1);
        when(mapper.findById(any())).thenAnswer(invocation -> tagRow(invocation.getArgument(0), "巡航", "#409EFF", true, 1));

        TagManagementResponse response = service.createTag(
                new TagCreateRequest(" 巡航 ", "#409eff"),
                new CurrentUser(userId, "admin", null, "ADMIN"),
                "127.0.0.1"
        );

        assertThat(response.name()).isEqualTo("巡航");
        assertThat(response.color()).isEqualTo("#409EFF");
        verify(auditLogService).recordSuccess(
                eq("CREATE_TAG"),
                eq("TAG"),
                eq(response.id().toString()),
                eq(userId),
                eq("127.0.0.1"),
                eq(null),
                any()
        );
    }

    @Test
    void createTagReportsDuplicateNameAsConflict() {
        when(mapper.insertTag(any())).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> service.createTag(
                new TagCreateRequest("巡航", "#409EFF"),
                new CurrentUser(UUID.randomUUID(), "admin", null, "ADMIN"),
                "127.0.0.1"
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void batchAddRequiresActiveRecordsAndEnabledTags() {
        UUID recordId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(mapper.countActiveRecords(List.of(recordId))).thenReturn(1);
        when(mapper.countEnabledTags(List.of(tagId))).thenReturn(1);
        when(mapper.insertRecordTags(List.of(recordId), List.of(tagId), userId)).thenReturn(1);

        var response = service.applyBatch(
                new BatchTagRequest(List.of(recordId), List.of(tagId), TagBatchMode.ADD),
                new CurrentUser(userId, "admin", null, "ADMIN"),
                "127.0.0.1"
        );

        assertThat(response.changed()).isEqualTo(1);
        verify(auditLogService).recordSuccess(
                eq("BATCH_TAG_ADD"),
                eq("DATA_RECORD_TAG"),
                eq("BATCH"),
                eq(userId),
                eq("127.0.0.1"),
                eq(null),
                any()
        );
    }

    private TagRow tagRow(UUID id, String name, String color, boolean enabled, int version) {
        TagRow row = new TagRow();
        row.setId(id);
        row.setName(name);
        row.setColor(color);
        row.setEnabled(enabled);
        row.setVersion(version);
        return row;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<TagMapper> provider(TagMapper mapper) {
        ObjectProvider<TagMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        return provider;
    }
}
