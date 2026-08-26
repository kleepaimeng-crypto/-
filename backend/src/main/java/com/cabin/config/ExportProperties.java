package com.cabin.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cabin.exports")
public record ExportProperties(String storageDir) {
    public ExportProperties {
        storageDir = storageDir == null || storageDir.isBlank() ? "exports" : storageDir.trim();
    }

    public Path storagePath() {
        return Path.of(storageDir).toAbsolutePath().normalize();
    }
}
