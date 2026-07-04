package com.cabin.login.dto;

import com.cabin.common.security.CurrentUser;
import com.cabin.login.entity.AppUser;
import java.util.UUID;

public record UserInfoResponse(
        UUID id,
        String username,
        String email,
        String roleCode
) {
    public static UserInfoResponse from(AppUser user) {
        return new UserInfoResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRoleCode());
    }

    public static UserInfoResponse from(CurrentUser user) {
        return new UserInfoResponse(user.id(), user.username(), user.email(), user.roleCode());
    }
}
