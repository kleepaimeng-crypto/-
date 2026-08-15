package com.cabin.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cabin.cockpit-video")
public record CockpitVideoProperties(
        boolean enabled,
        String playbackUrl
) {
    public CockpitVideoProperties {
        playbackUrl = normalize(playbackUrl);
        if (enabled) {
            validatePlaybackUrl(playbackUrl);
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validatePlaybackUrl(String value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "cabin.cockpit-video.playback-url must be configured when cockpit video is enabled"
            );
        }

        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
                throw invalidPlaybackUrl();
            }
            if (uri.getUserInfo() != null) {
                throw new IllegalArgumentException(
                        "cabin.cockpit-video.playback-url must not contain usernames or passwords"
                );
            }
        } catch (URISyntaxException exception) {
            throw invalidPlaybackUrl();
        }
    }

    private static IllegalArgumentException invalidPlaybackUrl() {
        return new IllegalArgumentException(
                "cabin.cockpit-video.playback-url must be an absolute http or https URL"
        );
    }
}
