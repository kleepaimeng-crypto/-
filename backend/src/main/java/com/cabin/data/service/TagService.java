package com.cabin.data.service;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.security.CurrentUser;
import com.cabin.data.dto.BatchTagRequest;
import com.cabin.data.dto.BatchTagResponse;
import com.cabin.data.dto.TagBatchMode;
import com.cabin.data.dto.TagCreateRequest;
import com.cabin.data.dto.TagDeleteRequest;
import com.cabin.data.dto.TagManagementResponse;
import com.cabin.data.dto.TagUpdateRequest;
import com.cabin.data.entity.TagRow;
import com.cabin.data.mapper.TagMapper;
import com.cabin.log.service.AuditLogService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {
    private final ObjectProvider<TagMapper> mapperProvider;
    private final AuditLogService auditLogService;

    public TagService(ObjectProvider<TagMapper> mapperProvider, AuditLogService auditLogService) {
        this.mapperProvider = mapperProvider;
        this.auditLogService = auditLogService;
    }

    public List<TagManagementResponse> listTags(boolean includeDisabled) {
        return mapper().findTags(includeDisabled).stream().map(this::toResponse).toList();
    }

    @Transactional
    public TagManagementResponse createTag(
            TagCreateRequest request,
            CurrentUser currentUser,
            String requestIp
    ) {
        TagMapper mapper = mapper();
        TagRow row = new TagRow();
        row.setId(UUID.randomUUID());
        row.setName(normalizeName(request.name()));
        row.setColor(normalizeColor(request.color()));
        row.setEnabled(true);
        row.setCreatedBy(currentUser.id());
        try {
            mapper.insertTag(row);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResponseCode.RESOURCE_CONFLICT, "标签名称已存在");
        }
        TagRow created = mapper.findById(row.getId());
        auditLogService.recordSuccess(
                "CREATE_TAG",
                "TAG",
                row.getId().toString(),
                currentUser.id(),
                requestIp,
                null,
                tagSnapshot(created)
        );
        return toResponse(created);
    }

    @Transactional
    public TagManagementResponse updateTag(
            UUID tagId,
            TagUpdateRequest request,
            CurrentUser currentUser,
            String requestIp
    ) {
        TagMapper mapper = mapper();
        TagRow before = requireTag(mapper, tagId);
        String name = request.name() == null ? null : normalizeName(request.name());
        String color = request.color() == null ? null : normalizeColor(request.color());
        if (name == null && color == null && request.enabled() == null) {
            throw new BusinessException(ResponseCode.VALIDATION_ERROR, "至少提供一个要更新的字段");
        }
        try {
            int updated = mapper.updateTag(tagId, name, color, request.enabled(), request.expectedVersion());
            if (updated == 0) {
                throw new BusinessException(ResponseCode.RESOURCE_CONFLICT, "标签版本已变化，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResponseCode.RESOURCE_CONFLICT, "标签名称已存在");
        }
        TagRow after = requireTag(mapper, tagId);
        auditLogService.recordSuccess(
                "UPDATE_TAG",
                "TAG",
                tagId.toString(),
                currentUser.id(),
                requestIp,
                tagSnapshot(before),
                tagSnapshot(after)
        );
        return toResponse(after);
    }

    @Transactional
    public void deleteTag(
            UUID tagId,
            TagDeleteRequest request,
            CurrentUser currentUser,
            String requestIp
    ) {
        String reason = normalizeReason(request.reason());
        TagMapper mapper = mapper();
        TagRow before = requireTag(mapper, tagId);
        if (!before.isEnabled()) {
            throw new BusinessException(ResponseCode.RESOURCE_CONFLICT, "标签已禁用");
        }
        int updated = mapper.disableTag(tagId);
        if (updated == 0) {
            throw new BusinessException(ResponseCode.RESOURCE_CONFLICT, "标签状态已变化，请刷新后重试");
        }
        TagRow after = requireTag(mapper, tagId);
        Map<String, Object> afterValue = tagSnapshot(after);
        afterValue.put("reason", reason);
        auditLogService.recordSuccess(
                "DELETE_TAG",
                "TAG",
                tagId.toString(),
                currentUser.id(),
                requestIp,
                tagSnapshot(before),
                afterValue
        );
    }

    @Transactional
    public BatchTagResponse applyBatch(
            BatchTagRequest request,
            CurrentUser currentUser,
            String requestIp
    ) {
        TagMapper mapper = mapper();
        List<UUID> recordIds = distinct(request.recordIds());
        List<UUID> tagIds = distinct(request.tagIds());
        int activeRecords = mapper.countActiveRecords(recordIds);
        if (activeRecords == 0) {
            throw new BusinessException(ResponseCode.RESOURCE_NOT_FOUND, "可操作的数据记录不存在");
        }
        int existingTags = request.mode() == TagBatchMode.ADD
                ? mapper.countEnabledTags(tagIds)
                : mapper.countTags(tagIds);
        if (existingTags != tagIds.size()) {
            throw new BusinessException(ResponseCode.RESOURCE_NOT_FOUND, "标签不存在或不可用");
        }
        int changed = request.mode() == TagBatchMode.ADD
                ? mapper.insertRecordTags(recordIds, tagIds, currentUser.id())
                : mapper.deleteRecordTags(recordIds, tagIds);
        Map<String, Object> afterValue = new LinkedHashMap<>();
        afterValue.put("mode", request.mode().name());
        afterValue.put("requestedRecords", recordIds.size());
        afterValue.put("requestedTags", tagIds.size());
        afterValue.put("changed", changed);
        afterValue.put("recordIds", sampleIds(recordIds));
        afterValue.put("tagIds", sampleIds(tagIds));
        auditLogService.recordSuccess(
                "BATCH_TAG_" + request.mode().name(),
                "DATA_RECORD_TAG",
                "BATCH",
                currentUser.id(),
                requestIp,
                null,
                afterValue
        );
        return new BatchTagResponse(recordIds.size(), tagIds.size(), changed);
    }

    private TagRow requireTag(TagMapper mapper, UUID tagId) {
        TagRow row = mapper.findById(tagId);
        if (row == null) {
            throw new BusinessException(ResponseCode.RESOURCE_NOT_FOUND, "标签不存在");
        }
        return row;
    }

    private TagManagementResponse toResponse(TagRow row) {
        return new TagManagementResponse(
                row.getId(),
                row.getName(),
                row.getColor(),
                row.isEnabled(),
                row.getVersion()
        );
    }

    private Map<String, Object> tagSnapshot(TagRow row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", row.getId());
        value.put("name", row.getName());
        value.put("color", row.getColor());
        value.put("enabled", row.isEnabled());
        value.put("version", row.getVersion());
        return value;
    }

    private String normalizeName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new BusinessException(ResponseCode.VALIDATION_ERROR, "标签名称长度必须为 1-64");
        }
        return normalized;
    }

    private String normalizeColor(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("^#[0-9A-F]{6}$")) {
            throw new BusinessException(ResponseCode.VALIDATION_ERROR, "标签颜色必须为 #RRGGBB");
        }
        return normalized;
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

    private TagMapper mapper() {
        TagMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
        }
        return mapper;
    }
}
