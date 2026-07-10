package com.cabin.data.service;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.security.CurrentUser;
import com.cabin.data.dto.AnnotationBatchCreateRequest;
import com.cabin.data.dto.AnnotationBatchCreateResponse;
import com.cabin.data.dto.AnnotationCreateRequest;
import com.cabin.data.dto.AnnotationDeleteRequest;
import com.cabin.data.dto.AnnotationResponse;
import com.cabin.data.dto.AnnotationUpdateRequest;
import com.cabin.data.entity.AnnotationRow;
import com.cabin.data.entity.DataRecordLifecycleRow;
import com.cabin.data.mapper.AnnotationMapper;
import com.cabin.data.mapper.DataRecordLifecycleMapper;
import com.cabin.log.service.AuditLogService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnotationService {
    private final ObjectProvider<AnnotationMapper> annotationMapperProvider;
    private final ObjectProvider<DataRecordLifecycleMapper> lifecycleMapperProvider;
    private final AuditLogService auditLogService;

    public AnnotationService(
            ObjectProvider<AnnotationMapper> annotationMapperProvider,
            ObjectProvider<DataRecordLifecycleMapper> lifecycleMapperProvider,
            AuditLogService auditLogService
    ) {
        this.annotationMapperProvider = annotationMapperProvider;
        this.lifecycleMapperProvider = lifecycleMapperProvider;
        this.auditLogService = auditLogService;
    }

    public List<AnnotationResponse> listAnnotations(UUID recordId, boolean includeDeleted) {
        ensureRecordReadable(recordId, includeDeleted);
        return annotationMapper().findForRecord(recordId, includeDeleted)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AnnotationResponse createAnnotation(
            UUID recordId,
            AnnotationCreateRequest request,
            CurrentUser currentUser,
            String requestIp
    ) {
        ensureRecordReadable(recordId, false);
        UUID annotationId = UUID.randomUUID();
        String content = normalizeContent(request.content());
        annotationMapper().insertAnnotation(annotationId, recordId, content, currentUser.id());
        AnnotationRow created = requireAnnotation(annotationId, false);
        auditLogService.recordSuccess(
                "CREATE_ANNOTATION",
                "DATA_ANNOTATION",
                annotationId.toString(),
                currentUser.id(),
                requestIp,
                null,
                annotationSnapshot(created)
        );
        return toResponse(created);
    }

    @Transactional
    public AnnotationBatchCreateResponse createAnnotations(
            AnnotationBatchCreateRequest request,
            CurrentUser currentUser,
            String requestIp
    ) {
        List<UUID> recordIds = distinct(request.recordIds());
        String content = normalizeContent(request.content());
        int activeRecords = lifecycleMapper().countActiveRecords(recordIds);
        if (activeRecords == 0) {
            throw new BusinessException(ResponseCode.RESOURCE_NOT_FOUND, "可批注的数据记录不存在");
        }
        int created = annotationMapper().insertBatch(recordIds, content, currentUser.id());
        Map<String, Object> afterValue = new LinkedHashMap<>();
        afterValue.put("requested", recordIds.size());
        afterValue.put("created", created);
        afterValue.put("skipped", recordIds.size() - created);
        afterValue.put("recordIds", sampleIds(recordIds));
        auditLogService.recordSuccess(
                "BATCH_CREATE_ANNOTATION",
                "DATA_ANNOTATION",
                "BATCH",
                currentUser.id(),
                requestIp,
                null,
                afterValue
        );
        return new AnnotationBatchCreateResponse(recordIds.size(), created, recordIds.size() - created);
    }

    @Transactional
    public AnnotationResponse updateAnnotation(
            UUID annotationId,
            AnnotationUpdateRequest request,
            CurrentUser currentUser,
            String requestIp
    ) {
        AnnotationRow before = requireAnnotation(annotationId, true);
        if (before.isDeleted()) {
            throw new BusinessException(ResponseCode.RESOURCE_CONFLICT, "批注已删除");
        }
        int updated = annotationMapper().updateAnnotation(
                annotationId,
                normalizeContent(request.content()),
                request.expectedVersion()
        );
        if (updated == 0) {
            throw new BusinessException(ResponseCode.RESOURCE_CONFLICT, "批注版本已变化，请刷新后重试");
        }
        AnnotationRow after = requireAnnotation(annotationId, false);
        auditLogService.recordSuccess(
                "UPDATE_ANNOTATION",
                "DATA_ANNOTATION",
                annotationId.toString(),
                currentUser.id(),
                requestIp,
                annotationSnapshot(before),
                annotationSnapshot(after)
        );
        return toResponse(after);
    }

    @Transactional
    public void deleteAnnotation(
            UUID annotationId,
            AnnotationDeleteRequest request,
            CurrentUser currentUser,
            String requestIp
    ) {
        String reason = normalizeReason(request.reason());
        AnnotationRow before = requireAnnotation(annotationId, true);
        if (before.isDeleted()) {
            throw new BusinessException(ResponseCode.RESOURCE_CONFLICT, "批注已删除");
        }
        int updated = annotationMapper().softDeleteAnnotation(
                annotationId,
                currentUser.id(),
                request.expectedVersion()
        );
        if (updated == 0) {
            throw new BusinessException(ResponseCode.RESOURCE_CONFLICT, "批注版本已变化，请刷新后重试");
        }
        AnnotationRow after = requireAnnotation(annotationId, true);
        Map<String, Object> afterValue = annotationSnapshot(after);
        afterValue.put("reason", reason);
        auditLogService.recordSuccess(
                "DELETE_ANNOTATION",
                "DATA_ANNOTATION",
                annotationId.toString(),
                currentUser.id(),
                requestIp,
                annotationSnapshot(before),
                afterValue
        );
    }

    private void ensureRecordReadable(UUID recordId, boolean includeDeleted) {
        DataRecordLifecycleRow row = lifecycleMapper().findLifecycle(recordId);
        if (row == null || (!includeDeleted && row.isDeleted())) {
            throw new BusinessException(ResponseCode.RESOURCE_NOT_FOUND, "数据记录不存在");
        }
    }

    private AnnotationRow requireAnnotation(UUID annotationId, boolean includeDeleted) {
        AnnotationRow row = annotationMapper().findById(annotationId, includeDeleted);
        if (row == null) {
            throw new BusinessException(ResponseCode.RESOURCE_NOT_FOUND, "批注不存在");
        }
        return row;
    }

    private AnnotationResponse toResponse(AnnotationRow row) {
        return new AnnotationResponse(
                row.getId(),
                row.getContent(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getVersion(),
                row.isDeleted()
        );
    }

    private Map<String, Object> annotationSnapshot(AnnotationRow row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", row.getId());
        value.put("recordId", row.getRecordId());
        value.put("content", row.getContent());
        value.put("version", row.getVersion());
        value.put("deleted", row.isDeleted());
        return value;
    }

    private String normalizeContent(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 2000) {
            throw new BusinessException(ResponseCode.VALIDATION_ERROR, "批注内容长度必须为 1-2000");
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

    private AnnotationMapper annotationMapper() {
        AnnotationMapper mapper = annotationMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
        }
        return mapper;
    }

    private DataRecordLifecycleMapper lifecycleMapper() {
        DataRecordLifecycleMapper mapper = lifecycleMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
        }
        return mapper;
    }
}
