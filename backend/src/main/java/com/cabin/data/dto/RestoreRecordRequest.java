package com.cabin.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RestoreRecordRequest(
        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason length must be less than or equal to 500")
        String reason
) {
}
