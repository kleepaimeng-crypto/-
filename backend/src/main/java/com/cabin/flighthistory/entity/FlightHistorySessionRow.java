package com.cabin.flighthistory.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public class FlightHistorySessionRow {
    private UUID id;
    private UUID sourceFlightSessionId;
    private String sourceSystemCode;
    private String sourceDeviceCode;
    private String sourceHost;
    private String flightNo;
    private String origin;
    private String destination;
    private String aircraftRegistrationNo;
    private String aircraftModel;
    private String airlineCode;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
    private Integer pointCount;
    private String finishReason;
    private OffsetDateTime archivedAt;
    public UUID getId() { return id; } public void setId(UUID value) { id = value; }
    public UUID getSourceFlightSessionId() { return sourceFlightSessionId; } public void setSourceFlightSessionId(UUID value) { sourceFlightSessionId = value; }
    public String getSourceSystemCode() { return sourceSystemCode; } public void setSourceSystemCode(String value) { sourceSystemCode = value; }
    public String getSourceDeviceCode() { return sourceDeviceCode; } public void setSourceDeviceCode(String value) { sourceDeviceCode = value; }
    public String getSourceHost() { return sourceHost; } public void setSourceHost(String value) { sourceHost = value; }
    public String getFlightNo() { return flightNo; } public void setFlightNo(String value) { flightNo = value; }
    public String getOrigin() { return origin; } public void setOrigin(String value) { origin = value; }
    public String getDestination() { return destination; } public void setDestination(String value) { destination = value; }
    public String getAircraftRegistrationNo() { return aircraftRegistrationNo; } public void setAircraftRegistrationNo(String value) { aircraftRegistrationNo = value; }
    public String getAircraftModel() { return aircraftModel; } public void setAircraftModel(String value) { aircraftModel = value; }
    public String getAirlineCode() { return airlineCode; } public void setAirlineCode(String value) { airlineCode = value; }
    public OffsetDateTime getStartedAt() { return startedAt; } public void setStartedAt(OffsetDateTime value) { startedAt = value; }
    public OffsetDateTime getEndedAt() { return endedAt; } public void setEndedAt(OffsetDateTime value) { endedAt = value; }
    public Integer getPointCount() { return pointCount; } public void setPointCount(Integer value) { pointCount = value; }
    public String getFinishReason() { return finishReason; } public void setFinishReason(String value) { finishReason = value; }
    public OffsetDateTime getArchivedAt() { return archivedAt; } public void setArchivedAt(OffsetDateTime value) { archivedAt = value; }
}
