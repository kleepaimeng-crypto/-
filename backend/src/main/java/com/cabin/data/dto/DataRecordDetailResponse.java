package com.cabin.data.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DataRecordDetailResponse(
        UUID id,
        RecordMetadataResponse metadata,
        JsonNode rawPayload,
        String rawText,
        Map<String, Object> parsedSummary,
        List<TagResponse> tags,
        List<AnnotationResponse> annotations,
        boolean deleted
) {
}
