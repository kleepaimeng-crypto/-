package com.cabin.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AnnotationBatchCreateRequest(
        @NotEmpty(message = "recordIds is required")
        @Size(max = 1000, message = "recordIds size must be less than or equal to 1000")
        List<UUID> recordIds,

        @NotBlank(message = "content is required")
        @Size(max = 2000, message = "content length must be less than or equal to 2000")
        String content
) {
}
