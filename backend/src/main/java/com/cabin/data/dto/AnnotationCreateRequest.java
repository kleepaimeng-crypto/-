package com.cabin.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnnotationCreateRequest(
        @NotBlank(message = "content is required")
        @Size(max = 2000, message = "content length must be less than or equal to 2000")
        String content
) {
}
