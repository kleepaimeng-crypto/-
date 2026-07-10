package com.cabin.data.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AnnotationResponse(
        UUID id,
        String content,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        int version,
        boolean deleted
) {
}
