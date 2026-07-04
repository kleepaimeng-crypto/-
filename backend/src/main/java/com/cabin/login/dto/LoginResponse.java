package com.cabin.login.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserInfoResponse user
) {
}
