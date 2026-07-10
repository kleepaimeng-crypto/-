package com.cabin.flighttrack.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class FlightTrackPointRow {
    private UUID flightSessionId;
    private UUID recordId;
    private OffsetDateTime sampleAt;
    private String sourceTimeText;
    private String flightNo;
    private String origin;
    private String destination;
    private String aircraftRegistrationNo;
    private String aircraftModel;
    private String airlineCode;
    private BigDecimal altitudeFt;
    private BigDecimal computedAirSpeedKt;
    private BigDecimal groundSpeedKt;
    private Double latitude;
    private Double longitude;
    private BigDecimal trackAngleDeg;
    private BigDecimal headingDeg;
    private BigDecimal pitchDeg;
    private BigDecimal rollDeg;
    private BigDecimal distanceToGoNm;
    private String destinationEtaText;
    private Long frameCount;

    public UUID getFlightSessionId() {
        return flightSessionId;
    }

    public void setFlightSessionId(UUID flightSessionId) {
        this.flightSessionId = flightSessionId;
    }

    public UUID getRecordId() {
        return recordId;
    }

    public void setRecordId(UUID recordId) {
        this.recordId = recordId;
    }

    public OffsetDateTime getSampleAt() {
        return sampleAt;
    }

    public void setSampleAt(OffsetDateTime sampleAt) {
        this.sampleAt = sampleAt;
    }

    public String getSourceTimeText() {
        return sourceTimeText;
    }

    public void setSourceTimeText(String sourceTimeText) {
        this.sourceTimeText = sourceTimeText;
    }

    public String getFlightNo() {
        return flightNo;
    }

    public void setFlightNo(String flightNo) {
        this.flightNo = flightNo;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getAircraftRegistrationNo() {
        return aircraftRegistrationNo;
    }

    public void setAircraftRegistrationNo(String aircraftRegistrationNo) {
        this.aircraftRegistrationNo = aircraftRegistrationNo;
    }

    public String getAircraftModel() {
        return aircraftModel;
    }

    public void setAircraftModel(String aircraftModel) {
        this.aircraftModel = aircraftModel;
    }

    public String getAirlineCode() {
        return airlineCode;
    }

    public void setAirlineCode(String airlineCode) {
        this.airlineCode = airlineCode;
    }

    public BigDecimal getAltitudeFt() {
        return altitudeFt;
    }

    public void setAltitudeFt(BigDecimal altitudeFt) {
        this.altitudeFt = altitudeFt;
    }

    public BigDecimal getComputedAirSpeedKt() {
        return computedAirSpeedKt;
    }

    public void setComputedAirSpeedKt(BigDecimal computedAirSpeedKt) {
        this.computedAirSpeedKt = computedAirSpeedKt;
    }

    public BigDecimal getGroundSpeedKt() {
        return groundSpeedKt;
    }

    public void setGroundSpeedKt(BigDecimal groundSpeedKt) {
        this.groundSpeedKt = groundSpeedKt;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getTrackAngleDeg() {
        return trackAngleDeg;
    }

    public void setTrackAngleDeg(BigDecimal trackAngleDeg) {
        this.trackAngleDeg = trackAngleDeg;
    }

    public BigDecimal getHeadingDeg() {
        return headingDeg;
    }

    public void setHeadingDeg(BigDecimal headingDeg) {
        this.headingDeg = headingDeg;
    }

    public BigDecimal getPitchDeg() {
        return pitchDeg;
    }

    public void setPitchDeg(BigDecimal pitchDeg) {
        this.pitchDeg = pitchDeg;
    }

    public BigDecimal getRollDeg() {
        return rollDeg;
    }

    public void setRollDeg(BigDecimal rollDeg) {
        this.rollDeg = rollDeg;
    }

    public BigDecimal getDistanceToGoNm() {
        return distanceToGoNm;
    }

    public void setDistanceToGoNm(BigDecimal distanceToGoNm) {
        this.distanceToGoNm = distanceToGoNm;
    }

    public String getDestinationEtaText() {
        return destinationEtaText;
    }

    public void setDestinationEtaText(String destinationEtaText) {
        this.destinationEtaText = destinationEtaText;
    }

    public Long getFrameCount() {
        return frameCount;
    }

    public void setFrameCount(Long frameCount) {
        this.frameCount = frameCount;
    }
}
