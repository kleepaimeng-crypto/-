package com.cabin.data.service;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.security.CurrentUser;
import com.cabin.data.dto.BatchDeleteRequest;
import com.cabin.data.dto.BatchDeleteResponse;
import com.cabin.data.dto.CodeNameOption;
import com.cabin.data.dto.DataRecordListItemResponse;
import com.cabin.data.dto.DeleteRecordRequest;
import com.cabin.data.dto.RestoreRecordRequest;
import com.cabin.data.dto.TagResponse;
import com.cabin.data.entity.DataRecordDetailRow;
import com.cabin.data.entity.DataRecordListRow;
import com.cabin.data.entity.TagAssignmentRow;
import com.cabin.data.mapper.DataRecordLifecycleMapper;
import com.cabin.data.mapper.DataRecordMapper;
import com.cabin.log.service.AuditLogService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataRecordLifecycleService {
    private final ObjectProvider<DataRecordLifecycleMapper> lifecycleMapperProvider;
    private final ObjectProvider<DataRecordMapper> dataRecordMapperProvider;
    private final AuditLogService auditLogService;

    public DataRecordLifecycleService(
            ObjectProvider<DataRecordLifecycleMapper> lifecycleMapperProvider,
            ObjectProvider<DataRecordMapper> dataRecordMapperProvider,
            AuditLogService auditLogService
    ) {
        this.lifecycleMapperProvider = lifecycleMapperProvider;
        this.dataRecordMapperProvider = dataRecordMapperProvider;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void deleteRecord(
            UUID recordId,
            DeleteRecordRequest request,
            CurrentUser currentUser,
            String requestIp
    ) {
        DataRecordDetailRow before = requireDetail(recordId, true);
        if (before.isDeleted()) {
            throw new BusinessException(ResponseCode.RECORD_ALREADY_DELETED, "记录已经软删除");
        }
        int updated = lifecycleMapper().softDeleteRecord(
                recordId,
                currentUser.id(),
                normalizeReason(request.reason()),
                request.expectedVersion()
        );
        if (updated == 0) {
            throw new BusinessException(ResponseCode.RESOURCE_CONFLICT, "记录版本已变化，请刷新后重试");
        }
        DataRecordDetailRow after = requireDetail(recordId, true);
        auditLogService.recordSuccess(
                "DELETE_RECORD",
                "DATA_RECORD",
                recordId.toString(),
                currentUser.id(),
                requestIp,
                recordSnapshot(before),
                recordSnapshot(after)
        );
    }

    @Transactional
    public BatchDeleteResponse batchDeleteRecords(
            BatchDeleteRequest request,
            CurrentUser currentUser,
            String requestIp
    ) {
        List<UUID> recordIds = distinct(request.recordIds());
        DataRecordLifecycleMapper mapper = lifecycleMapper();
        int existing = mapper.countExistingRecords(recordIds);
        if (existing == 0) {
            throw new BusinessException(ResponseCode.RESOURCE_NOT_FOUND, "数据记录不存在");
        }
        int deleted = mapper.batchSoftDeleteRecords(
                recordIds,
                currentUser.id(),
                normalizeReason(request.reason())
        );
        int skipped = recordIds.size() - deleted;
        Map<String, Object> afterValue = new LinkedHashMap<>();
        afterValue.put("requested", recordIds.size());
        afterValue.put("deleted", deleted);
        afterValue.put("skipped", skipped);
        afterValue.put("recordIds", sampleIds(recordIds));
        auditLogService.recordSuccess(
                "BATCH_DELETE_RECORD",
                "DATA_RECORD",
                "BATCH",
                currentUser.id(),
                requestIp,
                null,
                afterValue
        );
        return new BatchDeleteResponse(recordIds.size(), deleted, skipped);
    }

    @Transactional
    public DataRecordListItemResponse restoreRecord(
            UUID recordId,
            RestoreRecordRequest request,
            CurrentUser currentUser,
            String requestIp
    ) {
        String reason = normalizeReason(request.reason());
        DataRecordDetailRow before = requireDetail(recordId, true);
        if (!before.isDeleted()) {
            throw new BusinessException(ResponseCode.RECORD_NOT_DELETED, "记录未处于删除状态");
        }
        int updated = lifecycleMapper().restoreRecord(recordId);
        if (updated == 0) {
            throw new BusinessException(ResponseCode.RESOURCE_CONFLICT, "记录状态已变化，请刷新后重试");
        }
        DataRecordDetailRow after = requireDetail(recordId, true);
        Map<String, Object> afterValue = recordSnapshot(after);
        afterValue.put("reason", reason);
        auditLogService.recordSuccess(
                "RESTORE_RECORD",
                "DATA_RECORD",
                recordId.toString(),
                currentUser.id(),
                requestIp,
                recordSnapshot(before),
                afterValue
        );
        return toListItem(lifecycleMapper().findListRowById(recordId));
    }

    private DataRecordDetailRow requireDetail(UUID recordId, boolean includeDeleted) {
        DataRecordDetailRow row = dataRecordMapper().findDetail(recordId, includeDeleted);
        if (row == null) {
            throw new BusinessException(ResponseCode.RESOURCE_NOT_FOUND, "数据记录不存在");
        }
        return row;
    }

    private DataRecordListItemResponse toListItem(DataRecordListRow row) {
        List<TagResponse> tags = tagsForRecord(row.getId());
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

    private List<TagResponse> tagsForRecord(UUID recordId) {
        return dataRecordMapper().findTagsForRecords(List.of(recordId)).stream()
                .collect(Collectors.groupingBy(
                        TagAssignmentRow::getRecordId,
                        Collectors.mapping(this::toTag, Collectors.toList())
                ))
                .getOrDefault(recordId, List.of());
    }

    private TagResponse toTag(TagAssignmentRow row) {
        return new TagResponse(row.getTagId(), row.getName(), row.getColor());
    }

    private Map<String, Object> recordSnapshot(DataRecordDetailRow row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", row.getId());
        value.put("dataTypeCode", row.getDataTypeCode());
        value.put("sourceDeviceCode", row.getSourceDeviceCode());
        value.put("aircraftRegistrationNo", row.getAircraftRegistrationNo());
        value.put("flightNo", row.getFlightNo());
        value.put("deleted", row.isDeleted());
        value.put("deletedAt", row.getDeletedAt());
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

    private String normalizeReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new BusinessException(ResponseCode.VALIDATION_ERROR, "原因长度必须为 1-500");
        }
        return normalized;
    }

    private List<UUID> distinct(List<UUID> ids) {
        return ids.stream().distinct().toList();
    }

    private List<String> sampleIds(List<UUID> ids) {
        return ids.stream().limit(20).map(UUID::toString).toList();
    }

    private DataRecordLifecycleMapper lifecycleMapper() {
        DataRecordLifecycleMapper mapper = lifecycleMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
        }
        return mapper;
    }

    private DataRecordMapper dataRecordMapper() {
        DataRecordMapper mapper = dataRecordMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
        }
        return mapper;
    }
}
