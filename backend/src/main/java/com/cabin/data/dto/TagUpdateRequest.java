package com.cabin.data.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TagUpdateRequest(
        @Size(max = 64, message = "name length must be less than or equal to 64")
        String name,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be #RRGGBB")
        String color,

        Boolean enabled,

        @NotNull(message = "expectedVersion is required")
        @Min(value = 1, message = "expectedVersion must be greater than 0")
        Integer expectedVersion
) {
}
