package com.cabin.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TagCreateRequest(
        @NotBlank(message = "name is required")
        @Size(max = 64, message = "name length must be less than or equal to 64")
        String name,

        @NotBlank(message = "color is required")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be #RRGGBB")
        String color
) {
}
