package com.cabin.flighttrack.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public class FlightSessionRow {
    private UUID id;
    private String sourceSystemCode;
    private String sourceDeviceCode;
    private String sourceHost;
    private String flightNo;
    private String origin;
    private String destination;
    private String aircraftRegistrationNo;
    private String aircraftModel;
    private String airlineCode;
    private String status;
    private OffsetDateTime startedAt;
    private OffsetDateTime lastSampleAt;
    private OffsetDateTime lastReceivedAt;
    private OffsetDateTime endedAt;
    private Long lastFrameCount;
    private Long latestQarSampleId;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSourceSystemCode() {
        return sourceSystemCode;
    }

    public void setSourceSystemCode(String sourceSystemCode) {
        this.sourceSystemCode = sourceSystemCode;
    }

    public String getSourceDeviceCode() {
        return sourceDeviceCode;
    }

    public void setSourceDeviceCode(String sourceDeviceCode) {
        this.sourceDeviceCode = sourceDeviceCode;
    }

    public String getSourceHost() {
        return sourceHost;
    }

    public void setSourceHost(String sourceHost) {
        this.sourceHost = sourceHost;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getLastSampleAt() {
        return lastSampleAt;
    }

    public void setLastSampleAt(OffsetDateTime lastSampleAt) {
        this.lastSampleAt = lastSampleAt;
    }

    public OffsetDateTime getLastReceivedAt() {
        return lastReceivedAt;
    }

    public void setLastReceivedAt(OffsetDateTime lastReceivedAt) {
        this.lastReceivedAt = lastReceivedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(OffsetDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public Long getLastFrameCount() {
        return lastFrameCount;
    }

    public void setLastFrameCount(Long lastFrameCount) {
        this.lastFrameCount = lastFrameCount;
    }

    public Long getLatestQarSampleId() {
        return latestQarSampleId;
    }

    public void setLatestQarSampleId(Long latestQarSampleId) {
        this.latestQarSampleId = latestQarSampleId;
    }
}
