package com.cabin.data.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExportJobResponse(
        UUID id,
        String status,
        String dataTypeCode,
        String fileName,
        String format,
        int totalRows,
        int successRows,
        int failedRows,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
}
