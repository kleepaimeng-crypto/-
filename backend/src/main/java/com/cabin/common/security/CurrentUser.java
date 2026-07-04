package com.cabin.common.security;

import java.util.UUID;

public record CurrentUser(
        UUID id,
        String username,
        String email,
        String roleCode
) {
}
