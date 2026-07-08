package com.cabin.passenger.dto;

import java.math.BigDecimal;

public record SmartWindowSummaryResponse(
        BigDecimal averageBrightness,
        int disconnectedCount,
        int faultCount,
        int testCount
) {
}
