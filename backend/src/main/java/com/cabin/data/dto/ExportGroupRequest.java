package com.cabin.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record ExportGroupRequest(
        @NotBlank String dataTypeCode,
        @NotEmpty List<UUID> recordIds
) {
}
