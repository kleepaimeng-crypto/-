package com.cabin.data.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public class DataRecordListRow {
    private UUID id;
    private String aircraftRegistrationNo;
    private String aircraftModel;
    private String airlineCode;
    private String flightNo;
    private String origin;
    private String destination;
    private String sourceDeviceCode;
    private String dataTypeCode;
    private String dataTypeName;
    private OffsetDateTime sentAt;
    private OffsetDateTime receivedAt;
    private int payloadCount;
    private String parseStatus;
    private boolean deleted;
    private int version;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getSourceDeviceCode() {
        return sourceDeviceCode;
    }

    public void setSourceDeviceCode(String sourceDeviceCode) {
        this.sourceDeviceCode = sourceDeviceCode;
    }

    public String getDataTypeCode() {
        return dataTypeCode;
    }

    public void setDataTypeCode(String dataTypeCode) {
        this.dataTypeCode = dataTypeCode;
    }

    public String getDataTypeName() {
        return dataTypeName;
    }

    public void setDataTypeName(String dataTypeName) {
        this.dataTypeName = dataTypeName;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(OffsetDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public int getPayloadCount() {
        return payloadCount;
    }

    public void setPayloadCount(int payloadCount) {
        this.payloadCount = payloadCount;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
