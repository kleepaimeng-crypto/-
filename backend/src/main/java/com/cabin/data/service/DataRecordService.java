package com.cabin.data.service;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.PageResponse;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.security.CurrentUser;
import com.cabin.data.dto.AnnotationResponse;
import com.cabin.data.dto.CodeNameOption;
import com.cabin.data.dto.DataRecordDetailResponse;
import com.cabin.data.dto.DataRecordListItemResponse;
import com.cabin.data.dto.DataRecordQuery;
import com.cabin.data.dto.MetadataUpdateRequest;
import com.cabin.data.dto.RecordMetadataResponse;
import com.cabin.data.dto.TagResponse;
import com.cabin.data.entity.AnnotationRow;
import com.cabin.data.entity.DataRecordDetailRow;
import com.cabin.data.entity.DataRecordListRow;
import com.cabin.data.entity.TagAssignmentRow;
import com.cabin.data.entity.TagRow;
import com.cabin.data.mapper.DataRecordMapper;
import com.cabin.log.service.AuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataRecordService {
    private final ObjectProvider<DataRecordMapper> mapperProvider;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public DataRecordService(
            ObjectProvider<DataRecordMapper> mapperProvider,
            ObjectMapper objectMapper,
            AuditLogService auditLogService
    ) {
        this.mapperProvider = mapperProvider;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    public PageResponse<DataRecordListItemResponse> listRecords(DataRecordQuery query) {
        DataRecordMapper mapper = mapper();
        long total = mapper.countDataRecords(query);
        List<DataRecordListRow> rows = total == 0 ? List.of() : mapper.findDataRecordPage(query);
        Map<UUID, List<TagResponse>> tagsByRecord = tagsForRows(mapper, rows);
        List<DataRecordListItemResponse> items = rows.stream()
                .map(row -> toListItem(row, tagsByRecord.getOrDefault(row.getId(), List.of())))
                .toList();
        return PageResponse.of(items, query.getPage(), query.getPageSize(), total);
    }

    public DataRecordDetailResponse getDetail(UUID recordId, boolean includeDeleted) {
        DataRecordMapper mapper = mapper();
        DataRecordDetailRow row = mapper.findDetail(recordId, includeDeleted);
        if (row == null) {
            throw new BusinessException(ResponseCode.RESOURCE_NOT_FOUND, "数据记录不存在");
        }
        return toDetail(
                row,
                mapper.findTagsForRecord(recordId).stream().map(this::toTag).toList(),
                mapper.findAnnotationsForRecord(recordId).stream().map(this::toAnnotation).toList(),
                parsedSummary(mapper, row.getDataTypeCode(), recordId)
        );
    }

    @Transactional
    public RecordMetadataResponse updateMetadata(
            UUID recordId,
            MetadataUpdateRequest request,
            CurrentUser currentUser,
            String requestIp
    ) {
        DataRecordMapper mapper = mapper();
        DataRecordDetailRow before = mapper.findDetail(recordId, false);
        if (before == null) {
            throw new BusinessException(ResponseCode.RESOURCE_NOT_FOUND, "数据记录不存在");
        }

        MetadataUpdateRequest normalized = normalize(request);
        int updated = mapper.updateMetadata(recordId, normalized, normalized.expectedVersion());
        if (updated == 0) {
            throw new BusinessException(ResponseCode.RESOURCE_CONFLICT, "记录版本已变化，请刷新后重试");
        }

        DataRecordDetailRow after = mapper.findDetail(recordId, true);
        auditLogService.recordMetadataChange(
                recordId,
                currentUser.id(),
                requestIp,
                metadataSnapshot(before),
                metadataSnapshot(after)
        );
        return metadataResponse(after);
    }

    private Map<UUID, List<TagResponse>> tagsForRows(DataRecordMapper mapper, List<DataRecordListRow> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<UUID> recordIds = rows.stream().map(DataRecordListRow::getId).toList();
        return mapper.findTagsForRecords(recordIds).stream()
                .collect(Collectors.groupingBy(
                        TagAssignmentRow::getRecordId,
                        Collectors.mapping(this::toTag, Collectors.toList())
                ));
    }

    private DataRecordListItemResponse toListItem(DataRecordListRow row, List<TagResponse> tags) {
        return new DataRecordListItemResponse(
                row.getId(),
                row.getAircraftRegistrationNo(),
                row.getAircraftModel(),
                row.getAirlineCode(),
                row.getFlightNo(),
                row.getOrigin(),
                row.getDestination(),
                new CodeNameOption(row.getSourceDeviceCode(), deviceName(row.getSourceDeviceCode())),
                new CodeNameOption(row.getDataTypeCode(), row.getDataTypeName()),
                row.getSentAt(),
                row.getReceivedAt(),
                row.getPayloadCount(),
                row.getParseStatus(),
                tags,
                row.isDeleted(),
                row.getVersion()
        );
    }

    private DataRecordDetailResponse toDetail(
            DataRecordDetailRow row,
            List<TagResponse> tags,
            List<AnnotationResponse> annotations,
            Map<String, Object> parsedSummary
    ) {
        return new DataRecordDetailResponse(
                row.getId(),
                metadataResponse(row),
                rawPayload(row),
                row.getRawText(),
                parsedSummary,
                tags,
                annotations,
                row.isDeleted()
        );
    }

    private RecordMetadataResponse metadataResponse(DataRecordDetailRow row) {
        return new RecordMetadataResponse(
                row.getAircraftRegistrationNo(),
                row.getAircraftModel(),
                row.getAirlineCode(),
                row.getFlightNo(),
                row.getOrigin(),
                row.getDestination(),
                row.getDataTypeCode(),
                row.getSourceDeviceCode(),
                row.getSourceSystemCode(),
                row.getSentAt(),
                row.getReceivedAt(),
                row.getParseStatus(),
                row.getParseError(),
                row.getVersion()
        );
    }

    private JsonNode rawPayload(DataRecordDetailRow row) {
        if (row.getRawPayload() == null) {
            return null;
        }
        try {
            return objectMapper.readTree(row.getRawPayload());
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResponseCode.INTERNAL_ERROR, "原始报文读取失败");
        }
    }

    private Map<String, Object> parsedSummary(DataRecordMapper mapper, String dataTypeCode, UUID recordId) {
        Map<String, Object> summary = switch (dataTypeCode) {
            case "QAR" -> mapper.findQarSummary(recordId);
            case "GROUND_TASK" -> mapper.findTaskSummary(recordId);
            case "GROUND_TRAFFIC_RECORD" -> mapper.findTrafficSummary(recordId);
            case "GROUND_SESSION_SUMMARY" -> mapper.findSessionSummary(recordId);
            case "SMART_WINDOW_STATUS" -> mapper.findSmartWindowSummary(recordId);
            case "IFE_633_BEHAVIOR" -> mapper.findIfe633Summary(recordId);
            case "IFE_COCKRELL_BEHAVIOR" -> mapper.findIfeCockrellSummary(recordId);
            default -> Map.of();
        };
        return summary == null ? Map.of() : new LinkedHashMap<>(summary);
    }

    private TagResponse toTag(TagRow row) {
        return new TagResponse(row.getId(), row.getName(), row.getColor());
    }

    private TagResponse toTag(TagAssignmentRow row) {
        return new TagResponse(row.getTagId(), row.getName(), row.getColor());
    }

    private AnnotationResponse toAnnotation(AnnotationRow row) {
        return new AnnotationResponse(
                row.getId(),
                row.getContent(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getVersion(),
                row.isDeleted()
        );
    }

    private MetadataUpdateRequest normalize(MetadataUpdateRequest request) {
        return new MetadataUpdateRequest(
                trimRequired(request.aircraftRegistrationNo()),
                blankToNull(request.aircraftModel()),
                upperOrNull(request.airlineCode()),
                upperOrNull(request.flightNo()),
                airportOrNull("origin", request.origin()),
                airportOrNull("destination", request.destination()),
                trimRequired(request.sourceDeviceCode()),
                request.expectedVersion()
        );
    }

    private Map<String, Object> metadataSnapshot(DataRecordDetailRow row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("aircraftRegistrationNo", row.getAircraftRegistrationNo());
        value.put("aircraftModel", row.getAircraftModel());
        value.put("airlineCode", row.getAirlineCode());
        value.put("flightNo", row.getFlightNo());
        value.put("origin", row.getOrigin());
        value.put("destination", row.getDestination());
        value.put("sourceDeviceCode", row.getSourceDeviceCode());
        value.put("version", row.getVersion());
        return value;
    }

    private String deviceName(String code) {
        return switch (code) {
            case "SIM-QAR" -> "QAR 模拟设备";
            case "SIM-GROUND" -> "地面模拟设备";
            case "SIM-WINDOW" -> "智能舷窗模拟设备";
            case "SIM-IFE-633" -> "633 IFE 模拟设备";
            case "SIM-IFE-COCKRELL" -> "科克瑞尔 IFE 模拟设备";
            default -> code;
        };
    }

    private String airportOrNull(String field, String value) {
        String normalized = upperOrNull(value);
        if (normalized != null && !normalized.matches("[A-Z0-9]{4}")) {
            throw new BusinessException(ResponseCode.VALIDATION_ERROR, field + " 必须是 4 位机场代码");
        }
        return normalized;
    }

    private String upperOrNull(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trimRequired(String value) {
        return value == null ? null : value.trim();
    }

    private DataRecordMapper mapper() {
        DataRecordMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
        }
        return mapper;
    }
}
