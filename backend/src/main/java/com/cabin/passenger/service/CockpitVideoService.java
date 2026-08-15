package com.cabin.passenger.service;

import com.cabin.config.CockpitVideoProperties;
import com.cabin.passenger.dto.CockpitVideoConfigResponse;
import org.springframework.stereotype.Service;

@Service
public class CockpitVideoService {
    private static final String WEBRTC_PROTOCOL = "WEBRTC";

    private final CockpitVideoProperties properties;

    public CockpitVideoService(CockpitVideoProperties properties) {
        this.properties = properties;
    }

    public CockpitVideoConfigResponse getConfig() {
        return new CockpitVideoConfigResponse(
                properties.enabled(),
                WEBRTC_PROTOCOL,
                properties.enabled() ? properties.playbackUrl() : null
        );
    }
}
