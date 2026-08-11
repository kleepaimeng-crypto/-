package com.cabin.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UdpPropertiesTests {
    @Test
    void usesC929AsTheDefaultAircraftModel() {
        UdpProperties properties = new UdpProperties(
                true,
                0,
                0,
                null,
                null,
                null,
                null
        );

        assertThat(properties.aircraftRegistrationNo()).isEqualTo("B-TEST-001");
        assertThat(properties.aircraftModel()).isEqualTo("COMAC C929-700");
        assertThat(properties.airlineCode()).isEqualTo("CA");
    }

    @Test
    void keepsAnExplicitAircraftModelOverride() {
        UdpProperties properties = new UdpProperties(
                true,
                1_048_576,
                1_048_576,
                "B-CUSTOM",
                "Custom Aircraft",
                "MU",
                "Asia/Shanghai"
        );

        assertThat(properties.aircraftRegistrationNo()).isEqualTo("B-CUSTOM");
        assertThat(properties.aircraftModel()).isEqualTo("Custom Aircraft");
        assertThat(properties.airlineCode()).isEqualTo("MU");
    }
}
