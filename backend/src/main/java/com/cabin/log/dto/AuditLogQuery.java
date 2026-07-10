package com.cabin.log.dto;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ErrorDetail;
import com.cabin.common.response.ResponseCode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class AuditLogQuery {
    private final String action;
    private final String targetType;
    private final String targetId;
    private final UUID operatorId;
    private final OffsetDateTime createdFrom;
    private final OffsetDateTime createdTo;
    private final int page;
    private final int pageSize;

    public AuditLogQuery(
            String action,
            String targetType,
            String targetId,
            UUID operatorId,
            OffsetDateTime createdFrom,
            OffsetDateTime createdTo,
            int page,
            int pageSize
    ) {
        validatePage(page, pageSize);
        validateRange(createdFrom, createdTo);
        this.action = blankToNull(action);
        this.targetType = blankToNull(targetType);
        this.targetId = blankToNull(targetId);
        this.operatorId = operatorId;
        this.createdFrom = createdFrom;
        this.createdTo = createdTo;
        this.page = page;
        this.pageSize = pageSize;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public UUID getOperatorId() {
        return operatorId;
    }

    public OffsetDateTime getCreatedFrom() {
        return createdFrom;
    }

    public OffsetDateTime getCreatedTo() {
        return createdTo;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getOffset() {
        return (page - 1) * pageSize;
    }

    private static void validatePage(int page, int pageSize) {
        if (page < 1) {
            throw validation("page", "must be greater than 0");
        }
        if (pageSize != 20 && pageSize != 50 && pageSize != 100) {
            throw validation("pageSize", "must be 20, 50 or 100");
        }
    }

    private static void validateRange(OffsetDateTime createdFrom, OffsetDateTime createdTo) {
        if (createdFrom != null && createdTo != null && !createdTo.isAfter(createdFrom)) {
            throw new BusinessException(
                    ResponseCode.VALIDATION_ERROR,
                    "createdTo 必须晚于 createdFrom",
                    List.of(new ErrorDetail("createdTo", "invalid_range"))
            );
        }
    }

    private static BusinessException validation(String field, String reason) {
        return new BusinessException(
                ResponseCode.VALIDATION_ERROR,
                "参数校验失败",
                List.of(new ErrorDetail(field, reason))
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
