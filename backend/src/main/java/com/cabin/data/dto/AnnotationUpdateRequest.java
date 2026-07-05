package com.cabin.data.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AnnotationUpdateRequest(
        @NotBlank(message = "content is required")
        @Size(max = 2000, message = "content length must be less than or equal to 2000")
        String content,

        @NotNull(message = "expectedVersion is required")
        @Min(value = 1, message = "expectedVersion must be greater than 0")
        Integer expectedVersion
) {
}
