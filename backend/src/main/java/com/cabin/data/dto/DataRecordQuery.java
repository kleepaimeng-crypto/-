package com.cabin.data.dto;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ErrorDetail;
import com.cabin.common.response.ResponseCode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class DataRecordQuery {
    private final List<UUID> tagIds;
    private final String airlineCode;
    private final String flightNo;
    private final String sourceDeviceCode;
    private final String aircraftModel;
    private final String origin;
    private final String destination;
    private final String dataTypeCode;
    private final OffsetDateTime receivedFrom;
    private final OffsetDateTime receivedTo;
    private final boolean includeDeleted;
    private final int page;
    private final int pageSize;
    private final String sortBy;
    private final String sortDirection;
    private final String orderBySql;
    private final String sortDirectionSql;

    public DataRecordQuery(
            List<UUID> tagIds,
            String airlineCode,
            String flightNo,
            String sourceDeviceCode,
            String aircraftModel,
            String origin,
            String destination,
            String dataTypeCode,
            OffsetDateTime receivedFrom,
            OffsetDateTime receivedTo,
            boolean includeDeleted,
            int page,
            int pageSize,
            String sortBy,
            String sortDirection
    ) {
        validatePage(page, pageSize);
        validateRange(receivedFrom, receivedTo);
        this.tagIds = tagIds == null ? List.of() : List.copyOf(tagIds);
        this.airlineCode = upperOrNull(airlineCode);
        this.flightNo = upperOrNull(flightNo);
        this.sourceDeviceCode = blankToNull(sourceDeviceCode);
        this.aircraftModel = blankToNull(aircraftModel);
        this.origin = airportOrNull("origin", origin);
        this.destination = airportOrNull("destination", destination);
        this.dataTypeCode = upperOrNull(dataTypeCode);
        this.receivedFrom = receivedFrom;
        this.receivedTo = receivedTo;
        this.includeDeleted = includeDeleted;
        this.page = page;
        this.pageSize = pageSize;
        this.sortBy = blankToDefault(sortBy, "receivedAt");
        this.sortDirection = blankToDefault(sortDirection, "desc").toLowerCase(Locale.ROOT);
        this.orderBySql = orderBySql(this.sortBy);
        this.sortDirectionSql = directionSql(this.sortDirection);
    }

    public List<UUID> getTagIds() {
        return tagIds;
    }

    public int getTagCount() {
        return tagIds.size();
    }

    public String getAirlineCode() {
        return airlineCode;
    }

    public String getFlightNo() {
        return flightNo;
    }

    public String getSourceDeviceCode() {
        return sourceDeviceCode;
    }

    public String getAircraftModel() {
        return aircraftModel;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public String getDataTypeCode() {
        return dataTypeCode;
    }

    public OffsetDateTime getReceivedFrom() {
        return receivedFrom;
    }

    public OffsetDateTime getReceivedTo() {
        return receivedTo;
    }

    public boolean isIncludeDeleted() {
        return includeDeleted;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getOffset() {
        return (page - 1) * pageSize;
    }

    public String getSortBy() {
        return sortBy;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public String getOrderBySql() {
        return orderBySql;
    }

    public String getSortDirectionSql() {
        return sortDirectionSql;
    }

    private static void validatePage(int page, int pageSize) {
        if (page < 1) {
            throw validation("page", "must be greater than 0");
        }
        if (pageSize != 20 && pageSize != 50 && pageSize != 100) {
            throw validation("pageSize", "must be 20, 50 or 100");
        }
    }

    private static void validateRange(OffsetDateTime receivedFrom, OffsetDateTime receivedTo) {
        if (receivedFrom != null && receivedTo != null && !receivedTo.isAfter(receivedFrom)) {
            throw new BusinessException(
                    ResponseCode.VALIDATION_ERROR,
                    "receivedTo 必须晚于 receivedFrom",
                    List.of(new ErrorDetail("receivedTo", "invalid_range"))
            );
        }
    }

    private static String orderBySql(String sortBy) {
        return switch (blankToDefault(sortBy, "receivedAt")) {
            case "dataType" -> "dt.name";
            case "sentAt" -> "r.sent_at";
            case "receivedAt" -> "r.received_at";
            default -> throw validation("sortBy", "unsupported sort field");
        };
    }

    private static String directionSql(String sortDirection) {
        return switch (blankToDefault(sortDirection, "desc").toLowerCase(Locale.ROOT)) {
            case "asc" -> "ASC";
            case "desc" -> "DESC";
            default -> throw validation("sortDirection", "must be asc or desc");
        };
    }

    private static String airportOrNull(String field, String value) {
        String normalized = upperOrNull(value);
        if (normalized != null && !normalized.matches("[A-Z0-9]{4}")) {
            throw validation(field, "must be a 4-character airport code");
        }
        return normalized;
    }

    private static String upperOrNull(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static BusinessException validation(String field, String reason) {
        return new BusinessException(
                ResponseCode.VALIDATION_ERROR,
                "参数校验失败",
                List.of(new ErrorDetail(field, reason))
        );
    }
}
