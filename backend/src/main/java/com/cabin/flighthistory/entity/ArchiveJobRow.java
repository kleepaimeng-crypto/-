package com.cabin.flighthistory.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public class ArchiveJobRow {
    private UUID id;
    private UUID sourceFlightSessionId;
    private String finishReason;
    private int attemptCount;
    public UUID getId() { return id; } public void setId(UUID value) { id = value; }
    public UUID getSourceFlightSessionId() { return sourceFlightSessionId; } public void setSourceFlightSessionId(UUID value) { sourceFlightSessionId = value; }
    public String getFinishReason() { return finishReason; } public void setFinishReason(String value) { finishReason = value; }
    public int getAttemptCount() { return attemptCount; } public void setAttemptCount(int value) { attemptCount = value; }
}
