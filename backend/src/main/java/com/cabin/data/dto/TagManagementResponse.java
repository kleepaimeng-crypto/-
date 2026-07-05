package com.cabin.data.dto;

import java.util.UUID;

public record TagManagementResponse(
        UUID id,
        String name,
        String color,
        boolean enabled,
        int version
) {
}
