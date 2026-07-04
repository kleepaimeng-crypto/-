package com.cabin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cabin.jwt")
public record JwtProperties(
        String issuer,
        String secret,
        long expiresInSeconds
) {
    public long expiresInSeconds() {
        return expiresInSeconds <= 0 ? 7200 : expiresInSeconds;
    }
}
