package com.cabin.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class CockpitVideoPropertiesTests {
    @Test
    void allowsCockpitVideoToBeDisabledWithoutAPlaybackUrl() {
        CockpitVideoProperties properties = new CockpitVideoProperties(false, "  ");

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.playbackUrl()).isNull();
    }

    @Test
    void trimsAValidPlaybackUrl() {
        CockpitVideoProperties properties = new CockpitVideoProperties(
                true,
                "  http://127.0.0.1:8889/cockpit  "
        );

        assertThat(properties.playbackUrl()).isEqualTo("http://127.0.0.1:8889/cockpit");
    }

    @Test
    void rejectsAnEnabledConfigWithoutAPlaybackUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CockpitVideoProperties(true, null))
                .withMessageContaining("must be configured");
    }

    @Test
    void rejectsNonHttpPlaybackUrls() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CockpitVideoProperties(true, "rtsp://127.0.0.1:8554/cockpit"))
                .withMessageContaining("http or https");
    }

    @Test
    void rejectsPlaybackUrlsContainingCredentials() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CockpitVideoProperties(true, "http://user:pass@127.0.0.1:8889/cockpit"))
                .withMessageContaining("must not contain usernames or passwords");
    }
}
