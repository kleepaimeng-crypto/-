package com.cabin.user.dto;

import com.cabin.user.entity.UserRow;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        String username,
        String email,
        String roleCode,
        String status,
        OffsetDateTime lastLoginAt,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt
) {
    public static UserSummaryResponse from(UserRow row) {
        return new UserSummaryResponse(
                row.getId(),
                row.getUsername(),
                row.getEmail(),
                row.getRoleCode(),
                row.getStatus(),
                row.getLastLoginAt(),
                row.getVersion(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getDeletedAt()
        );
    }
}
