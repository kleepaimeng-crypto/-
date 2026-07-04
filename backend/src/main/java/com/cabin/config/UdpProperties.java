package com.cabin.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cabin.udp")
public record UdpProperties(
        boolean enabled,
        int socketReceiveBufferBytes,
        int maxDatagramBytes,
        String aircraftRegistrationNo,
        String aircraftModel,
        String airlineCode,
        String zoneId
) {
    public int socketReceiveBufferBytes() {
        return socketReceiveBufferBytes <= 0 ? 1_048_576 : socketReceiveBufferBytes;
    }

    public int maxDatagramBytes() {
        return maxDatagramBytes <= 0 ? 1_048_576 : maxDatagramBytes;
    }

    public String aircraftRegistrationNo() {
        return blankToDefault(aircraftRegistrationNo, "B-TEST-001");
    }

    public String aircraftModel() {
        return blankToDefault(aircraftModel, "Boeing 777-300ER");
    }

    public String airlineCode() {
        return blankToDefault(airlineCode, "CA");
    }

    public ZoneId zone() {
        return ZoneId.of(blankToDefault(zoneId, "Asia/Shanghai"));
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
