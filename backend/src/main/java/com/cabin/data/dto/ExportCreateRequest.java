package com.cabin.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ExportCreateRequest(
        @NotEmpty List<@Valid ExportGroupRequest> groups
) {
}
