package com.cabin.udp.dto;

import java.time.OffsetDateTime;

public record UdpChannelSnapshot(
        String dataTypeCode,
        String messageType,
        int port,
        long receivedCount,
        long failedCount,
        OffsetDateTime lastReceivedAt,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime lastFailureAt,
        String lastError
) {
}


