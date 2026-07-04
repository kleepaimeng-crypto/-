package com.cabin.udp.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public class DataRecord {
    private UUID id;
    private String dataTypeCode;
    private String sourceSystemCode;
    private String sourceDeviceCode;
    private String sourceHost;
    private Integer sourcePort;
    private String aircraftRegistrationNo;
    private String aircraftModel;
    private String airlineCode;
    private String flightNo;
    private String origin;
    private String destination;
    private OffsetDateTime sentAt;
    private OffsetDateTime receivedAt;
    private Integer payloadCount;
    private String rawPayload;
    private String rawText;
    private String parseStatus;
    private String parseError;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getDataTypeCode() {
        return dataTypeCode;
    }

    public void setDataTypeCode(String dataTypeCode) {
        this.dataTypeCode = dataTypeCode;
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

    public Integer getSourcePort() {
        return sourcePort;
    }

    public void setSourcePort(Integer sourcePort) {
        this.sourcePort = sourcePort;
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

    public Integer getPayloadCount() {
        return payloadCount;
    }

    public void setPayloadCount(Integer payloadCount) {
        this.payloadCount = payloadCount;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    public String getParseError() {
        return parseError;
    }

    public void setParseError(String parseError) {
        this.parseError = parseError;
    }
}


