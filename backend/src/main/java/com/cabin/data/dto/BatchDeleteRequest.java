package com.cabin.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BatchDeleteRequest(
        @NotEmpty(message = "recordIds is required")
        @Size(max = 1000, message = "recordIds size must be less than or equal to 1000")
        List<UUID> recordIds,

        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason length must be less than or equal to 500")
        String reason
) {
}
