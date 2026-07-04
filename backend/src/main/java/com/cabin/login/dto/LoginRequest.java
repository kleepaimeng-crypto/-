package com.cabin.login.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "username required")
        @Size(max = 64, message = "username too long")
        String username,

        @NotBlank(message = "password required")
        @Size(max = 200, message = "password too long")
        String password
) {
}
