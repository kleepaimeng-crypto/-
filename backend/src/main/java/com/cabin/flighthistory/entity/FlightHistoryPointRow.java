package com.cabin.flighthistory.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class FlightHistoryPointRow {
    private Long sourceQarSampleId;
    private OffsetDateTime sampleAt;
    private String sourceTimeText;
    private Long frameCount;
    private String airGroundStatus;
    private Double latitude;
    private Double longitude;
    private BigDecimal altitudeFt;
    private BigDecimal groundSpeedKt;
    private BigDecimal computedAirSpeedKt;
    private BigDecimal trackAngleDeg;
    private BigDecimal headingDeg;
    private BigDecimal pitchDeg;
    private BigDecimal rollDeg;
    private BigDecimal distanceToGoNm;
    private String destinationEtaText;
    public Long getSourceQarSampleId() { return sourceQarSampleId; } public void setSourceQarSampleId(Long value) { sourceQarSampleId = value; }
    public OffsetDateTime getSampleAt() { return sampleAt; } public void setSampleAt(OffsetDateTime value) { sampleAt = value; }
    public String getSourceTimeText() { return sourceTimeText; } public void setSourceTimeText(String value) { sourceTimeText = value; }
    public Long getFrameCount() { return frameCount; } public void setFrameCount(Long value) { frameCount = value; }
    public String getAirGroundStatus() { return airGroundStatus; } public void setAirGroundStatus(String value) { airGroundStatus = value; }
    public Double getLatitude() { return latitude; } public void setLatitude(Double value) { latitude = value; }
    public Double getLongitude() { return longitude; } public void setLongitude(Double value) { longitude = value; }
    public BigDecimal getAltitudeFt() { return altitudeFt; } public void setAltitudeFt(BigDecimal value) { altitudeFt = value; }
    public BigDecimal getGroundSpeedKt() { return groundSpeedKt; } public void setGroundSpeedKt(BigDecimal value) { groundSpeedKt = value; }
    public BigDecimal getComputedAirSpeedKt() { return computedAirSpeedKt; } public void setComputedAirSpeedKt(BigDecimal value) { computedAirSpeedKt = value; }
    public BigDecimal getTrackAngleDeg() { return trackAngleDeg; } public void setTrackAngleDeg(BigDecimal value) { trackAngleDeg = value; }
    public BigDecimal getHeadingDeg() { return headingDeg; } public void setHeadingDeg(BigDecimal value) { headingDeg = value; }
    public BigDecimal getPitchDeg() { return pitchDeg; } public void setPitchDeg(BigDecimal value) { pitchDeg = value; }
    public BigDecimal getRollDeg() { return rollDeg; } public void setRollDeg(BigDecimal value) { rollDeg = value; }
    public BigDecimal getDistanceToGoNm() { return distanceToGoNm; } public void setDistanceToGoNm(BigDecimal value) { distanceToGoNm = value; }
    public String getDestinationEtaText() { return destinationEtaText; } public void setDestinationEtaText(String value) { destinationEtaText = value; }
}
