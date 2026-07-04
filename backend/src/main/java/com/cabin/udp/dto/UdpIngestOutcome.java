package com.cabin.udp.dto;

import java.util.UUID;

public record UdpIngestOutcome(
        UUID recordId,
        String dataTypeCode,
        String parseStatus,
        int businessRowCount,
        String errorMessage
) {
    public boolean failed() {
        return "FAILED".equals(parseStatus);
    }
}


