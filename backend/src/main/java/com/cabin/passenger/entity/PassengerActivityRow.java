package com.cabin.passenger.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class PassengerActivityRow {
    private String passengerId;
    private String seatNo;
    private String cabinClass;
    private String behaviorType;
    private String title;
    private String typesText;
    private String action;
    private String domain;
    private String url;
    private Long trafficBytes;
    private BigDecimal bandwidthMbps;
    private Long windowBytes;
    private OffsetDateTime eventAt;
    private OffsetDateTime bandwidthUpdatedAt;
    private UUID sourceRecordId;

    public String getPassengerId() { return passengerId; }
    public void setPassengerId(String passengerId) { this.passengerId = passengerId; }
    public String getSeatNo() { return seatNo; }
    public void setSeatNo(String seatNo) { this.seatNo = seatNo; }
    public String getCabinClass() { return cabinClass; }
    public void setCabinClass(String cabinClass) { this.cabinClass = cabinClass; }
    public String getBehaviorType() { return behaviorType; }
    public void setBehaviorType(String behaviorType) { this.behaviorType = behaviorType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTypesText() { return typesText; }
    public void setTypesText(String typesText) { this.typesText = typesText; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Long getTrafficBytes() { return trafficBytes; }
    public void setTrafficBytes(Long trafficBytes) { this.trafficBytes = trafficBytes; }
    public BigDecimal getBandwidthMbps() { return bandwidthMbps; }
    public void setBandwidthMbps(BigDecimal bandwidthMbps) { this.bandwidthMbps = bandwidthMbps; }
    public Long getWindowBytes() { return windowBytes; }
    public void setWindowBytes(Long windowBytes) { this.windowBytes = windowBytes; }
    public OffsetDateTime getEventAt() { return eventAt; }
    public void setEventAt(OffsetDateTime eventAt) { this.eventAt = eventAt; }
    public OffsetDateTime getBandwidthUpdatedAt() { return bandwidthUpdatedAt; }
    public void setBandwidthUpdatedAt(OffsetDateTime bandwidthUpdatedAt) { this.bandwidthUpdatedAt = bandwidthUpdatedAt; }
    public UUID getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(UUID sourceRecordId) { this.sourceRecordId = sourceRecordId; }
}
