package com.cabin.udp.service;

import com.cabin.udp.entity.DataTypeConfig;
import com.cabin.udp.dto.UdpChannelSnapshot;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

class UdpChannelStatus {
    private final DataTypeConfig config;
    private final AtomicLong receivedCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();
    private volatile OffsetDateTime lastReceivedAt;
    private volatile OffsetDateTime lastSuccessAt;
    private volatile OffsetDateTime lastFailureAt;
    private volatile String lastError;

    UdpChannelStatus(DataTypeConfig config) {
        this.config = config;
    }

    void markReceived(OffsetDateTime receivedAt) {
        receivedCount.incrementAndGet();
        lastReceivedAt = receivedAt;
    }

    void markSuccess(OffsetDateTime receivedAt) {
        lastSuccessAt = receivedAt;
    }

    void markFailure(OffsetDateTime receivedAt, String error) {
        failedCount.incrementAndGet();
        lastFailureAt = receivedAt;
        lastError = error;
    }

    UdpChannelSnapshot snapshot() {
        return new UdpChannelSnapshot(
                config.getCode(),
                config.getMessageType(),
                config.getUdpPort(),
                receivedCount.get(),
                failedCount.get(),
                lastReceivedAt,
                lastSuccessAt,
                lastFailureAt,
                lastError
        );
    }
}

