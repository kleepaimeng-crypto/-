package com.cabin.passenger.dto;

public record CockpitVideoConfigResponse(
        boolean enabled,
        String protocol,
        String playbackUrl
) {
}
