package com.cabin.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MetadataUpdateRequest(
        @NotBlank(message = "aircraftRegistrationNo is required")
        @Size(max = 32, message = "aircraftRegistrationNo too long")
        String aircraftRegistrationNo,

        @Size(max = 128, message = "aircraftModel too long")
        String aircraftModel,

        @Size(max = 16, message = "airlineCode too long")
        String airlineCode,

        @Size(max = 20, message = "flightNo too long")
        String flightNo,

        @Size(max = 4, message = "origin too long")
        String origin,

        @Size(max = 4, message = "destination too long")
        String destination,

        @NotBlank(message = "sourceDeviceCode is required")
        @Size(max = 64, message = "sourceDeviceCode too long")
        String sourceDeviceCode,

        @NotNull(message = "expectedVersion is required")
        @Positive(message = "expectedVersion must be positive")
        Integer expectedVersion
) {
}
