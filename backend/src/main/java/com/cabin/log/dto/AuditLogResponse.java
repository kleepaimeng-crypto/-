package com.cabin.log.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditLogResponse(
        long id,
        String action,
        String targetType,
        String targetId,
        UUID operatorId,
        String requestIp,
        JsonNode beforeValue,
        JsonNode afterValue,
        String result,
        String traceId,
        OffsetDateTime createdAt
) {
}
