package com.cabin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cabin.bootstrap-admin")
public record BootstrapAdminProperties(
        String username,
        String password,
        String email
) {
}
