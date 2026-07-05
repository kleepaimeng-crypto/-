package com.cabin.data.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BatchTagRequest(
        @NotEmpty(message = "recordIds is required")
        @Size(max = 1000, message = "recordIds size must be less than or equal to 1000")
        List<UUID> recordIds,

        @NotEmpty(message = "tagIds is required")
        @Size(max = 20, message = "tagIds size must be less than or equal to 20")
        List<UUID> tagIds,

        @NotNull(message = "mode is required")
        TagBatchMode mode
) {
}
