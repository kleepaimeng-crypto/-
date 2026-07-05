package com.cabin.data.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeleteRecordRequest(
        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason length must be less than or equal to 500")
        String reason,

        @NotNull(message = "expectedVersion is required")
        @Min(value = 1, message = "expectedVersion must be greater than 0")
        Integer expectedVersion
) {
}
