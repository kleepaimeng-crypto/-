package com.cabin.data.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.PageResponse;
import com.cabin.common.security.CurrentUser;
import com.cabin.data.dto.DataRecordDetailResponse;
import com.cabin.data.dto.DataRecordListItemResponse;
import com.cabin.data.dto.DataRecordQuery;
import com.cabin.data.dto.MetadataUpdateRequest;
import com.cabin.data.entity.DataRecordDetailRow;
import com.cabin.data.entity.DataRecordListRow;
import com.cabin.data.entity.TagAssignmentRow;
import com.cabin.data.mapper.DataRecordMapper;
import com.cabin.log.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class DataRecordServiceTests {
    private final DataRecordMapper mapper = mock(DataRecordMapper.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final DataRecordService service = new DataRecordService(
            provider(mapper),
            new ObjectMapper(),
            auditLogService
    );

    @Test
    void listRecordsReturnsTagsAndPaging() {
        UUID recordId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        DataRecordListRow row = listRow(recordId);
        TagAssignmentRow tag = new TagAssignmentRow();
        tag.setRecordId(recordId);
        tag.setTagId(tagId);
        tag.setName("巡航");
        tag.setColor("#409EFF");
        DataRecordQuery query = new DataRecordQuery(
                List.of(), null, null, null, null, null, null, null,
                null, null, false, 1, 20, "receivedAt", "desc"
        );
        when(mapper.countDataRecords(query)).thenReturn(1L);
        when(mapper.findDataRecordPage(query)).thenReturn(List.of(row));
        when(mapper.findTagsForRecords(List.of(recordId))).thenReturn(List.of(tag));

        PageResponse<DataRecordListItemResponse> page = service.listRecords(query);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(recordId);
            assertThat(item.dataType().name()).isEqualTo("QAR 飞行数据");
            assertThat(item.tags()).singleElement().satisfies(itemTag -> assertThat(itemTag.name()).isEqualTo("巡航"));
        });
    }

    @Test
    void detailParsesRawPayloadAsJsonObjectAndAddsSummary() {
        UUID recordId = UUID.randomUUID();
        DataRecordDetailRow row = detailRow(recordId, 1);
        row.setRawPayload("{\"frameCount\":42}");
        when(mapper.findDetail(recordId, false)).thenReturn(row);
        when(mapper.findTagsForRecord(recordId)).thenReturn(List.of());
        when(mapper.findAnnotationsForRecord(recordId)).thenReturn(List.of());
        when(mapper.findQarSummary(recordId)).thenReturn(Map.of("frameCount", 42));

        DataRecordDetailResponse detail = service.getDetail(recordId, false);

        assertThat(detail.rawPayload().get("frameCount").asInt()).isEqualTo(42);
        assertThat(detail.parsedSummary()).containsEntry("frameCount", 42);
        assertThat(detail.metadata().dataTypeCode()).isEqualTo("QAR");
    }

    @Test
    void updateMetadataUsesOptimisticLockAndWritesAudit() {
        UUID recordId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DataRecordDetailRow before = detailRow(recordId, 1);
        DataRecordDetailRow after = detailRow(recordId, 2);
        after.setFlightNo("CA9999");
        when(mapper.findDetail(recordId, false)).thenReturn(before);
        when(mapper.updateMetadata(any(), any(), org.mockito.ArgumentMatchers.eq(1))).thenReturn(1);
        when(mapper.findDetail(recordId, true)).thenReturn(after);

        service.updateMetadata(
                recordId,
                new MetadataUpdateRequest(
                        " B-TEST-001 ",
                        "Boeing 777-300ER",
                        "ca",
                        "ca9999",
                        "zbaa",
                        "zspd",
                        "SIM-QAR",
                        1
                ),
                new CurrentUser(userId, "admin", null, "ADMIN"),
                "127.0.0.1"
        );

        verify(auditLogService).recordMetadataChange(
                org.mockito.ArgumentMatchers.eq(recordId),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("127.0.0.1"),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyMap()
        );
    }

    @Test
    void updateMetadataReportsConflictWhenVersionChanged() {
        UUID recordId = UUID.randomUUID();
        when(mapper.findDetail(recordId, false)).thenReturn(detailRow(recordId, 2));
        when(mapper.updateMetadata(any(), any(), org.mockito.ArgumentMatchers.eq(1))).thenReturn(0);

        assertThatThrownBy(() -> service.updateMetadata(
                recordId,
                new MetadataUpdateRequest("B-TEST-001", null, null, null, null, null, "SIM-QAR", 1),
                new CurrentUser(UUID.randomUUID(), "admin", null, "ADMIN"),
                "127.0.0.1"
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("版本");
    }

    private DataRecordListRow listRow(UUID recordId) {
        DataRecordListRow row = new DataRecordListRow();
        row.setId(recordId);
        row.setAircraftRegistrationNo("B-TEST-001");
        row.setAircraftModel("Boeing 777-300ER");
        row.setAirlineCode("CA");
        row.setFlightNo("CA4732");
        row.setOrigin("ZBAA");
        row.setDestination("ZSPD");
        row.setSourceDeviceCode("SIM-QAR");
        row.setDataTypeCode("QAR");
        row.setDataTypeName("QAR 飞行数据");
        row.setSentAt(OffsetDateTime.parse("2026-07-04T12:00:00+08:00"));
        row.setReceivedAt(OffsetDateTime.parse("2026-07-04T12:00:01+08:00"));
        row.setPayloadCount(1);
        row.setParseStatus("PARSED");
        row.setVersion(1);
        return row;
    }

    private DataRecordDetailRow detailRow(UUID recordId, int version) {
        DataRecordDetailRow row = new DataRecordDetailRow();
        row.setId(recordId);
        row.setAircraftRegistrationNo("B-TEST-001");
        row.setAircraftModel("Boeing 777-300ER");
        row.setAirlineCode("CA");
        row.setFlightNo("CA4732");
        row.setOrigin("ZBAA");
        row.setDestination("ZSPD");
        row.setDataTypeCode("QAR");
        row.setSourceDeviceCode("SIM-QAR");
        row.setSourceSystemCode("SIMULATOR");
        row.setSentAt(OffsetDateTime.parse("2026-07-04T12:00:00+08:00"));
        row.setReceivedAt(OffsetDateTime.parse("2026-07-04T12:00:01+08:00"));
        row.setParseStatus("PARSED");
        row.setVersion(version);
        return row;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<DataRecordMapper> provider(DataRecordMapper mapper) {
        ObjectProvider<DataRecordMapper> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mapper);
        return provider;
    }
}
