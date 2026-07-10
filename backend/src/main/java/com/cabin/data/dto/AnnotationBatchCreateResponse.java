package com.cabin.data.dto;

public record AnnotationBatchCreateResponse(
        int requested,
        int created,
        int skipped
) {
}
