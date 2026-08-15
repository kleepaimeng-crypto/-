package com.cabin.passenger.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cabin.config.CockpitVideoProperties;
import com.cabin.passenger.dto.CockpitVideoConfigResponse;
import org.junit.jupiter.api.Test;

class CockpitVideoServiceTests {
    @Test
    void returnsTheConfiguredWebRtcPlaybackUrl() {
        CockpitVideoService service = new CockpitVideoService(
                new CockpitVideoProperties(true, "http://127.0.0.1:8889/cockpit")
        );

        CockpitVideoConfigResponse response = service.getConfig();

        assertThat(response.enabled()).isTrue();
        assertThat(response.protocol()).isEqualTo("WEBRTC");
        assertThat(response.playbackUrl()).isEqualTo("http://127.0.0.1:8889/cockpit");
    }

    @Test
    void hidesThePlaybackUrlWhenCockpitVideoIsDisabled() {
        CockpitVideoService service = new CockpitVideoService(
                new CockpitVideoProperties(false, "http://127.0.0.1:8889/cockpit")
        );

        CockpitVideoConfigResponse response = service.getConfig();

        assertThat(response.enabled()).isFalse();
        assertThat(response.protocol()).isEqualTo("WEBRTC");
        assertThat(response.playbackUrl()).isNull();
    }
}
