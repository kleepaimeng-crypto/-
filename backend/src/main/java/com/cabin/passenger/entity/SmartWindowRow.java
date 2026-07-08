package com.cabin.passenger.entity;

import java.time.OffsetDateTime;

public class SmartWindowRow {
    private int windowId;
    private int zoneId;
    private int brightnessLevel;
    private boolean connected;
    private String status;
    private OffsetDateTime updatedAt;

    public int getWindowId() {
        return windowId;
    }

    public void setWindowId(int windowId) {
        this.windowId = windowId;
    }

    public int getZoneId() {
        return zoneId;
    }

    public void setZoneId(int zoneId) {
        this.zoneId = zoneId;
    }

    public int getBrightnessLevel() {
        return brightnessLevel;
    }

    public void setBrightnessLevel(int brightnessLevel) {
        this.brightnessLevel = brightnessLevel;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
