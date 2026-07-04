package com.cabin.udp.service;

import com.cabin.config.UdpProperties;
import com.cabin.udp.entity.DataRecord;
import com.cabin.udp.entity.DataTypeConfig;
import com.cabin.udp.dto.ParsedUdpPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UdpPayloadParser {
    private static final DateTimeFormatter COMPACT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final ObjectMapper objectMapper;
    private final UdpProperties properties;

    public UdpPayloadParser(ObjectMapper objectMapper, UdpProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ParsedUdpPayload parse(
            DataTypeConfig config,
            JsonNode root,
            String rawPayload,
            OffsetDateTime receivedAt,
            String sourceHost,
            Integer sourcePort
    ) {
        DataRecord record = baseRecord(config, receivedAt, sourceHost, sourcePort);
        record.setRawPayload(rawPayload);
        record.setSentAt(parseRootSentAt(root, receivedAt));

        try {
            ParsedUdpPayload parsed = switch (config.getCode()) {
                case "QAR" -> parseQar(record, root, receivedAt);
                case "GROUND_TASK" -> parseGroundTask(record, root);
                case "GROUND_TRAFFIC_RECORD" -> parseTraffic(record, root);
                case "GROUND_SESSION_SUMMARY" -> parseSession(record, root);
                case "SMART_WINDOW_STATUS" -> parseSmartWindow(record, root);
                case "IFE_633_BEHAVIOR" -> parseIfe(record, root, false);
                case "IFE_COCKRELL_BEHAVIOR" -> parseIfe(record, root, true);
                default -> throw new PayloadParseException("unsupported data type " + config.getCode());
            };
            enrichAirline(parsed.record());
            return parsed;
        } catch (RuntimeException exception) {
            markFailed(record, summarize(exception));
            enrichAirline(record);
            return new ParsedUdpPayload(record, List.of());
        }
    }

    public ParsedUdpPayload failedTextPayload(
            DataTypeConfig config,
            String rawText,
            String parseError,
            OffsetDateTime receivedAt,
            String sourceHost,
            Integer sourcePort
    ) {
        DataRecord record = baseRecord(config, receivedAt, sourceHost, sourcePort);
        record.setRawText(rawText);
        markFailed(record, parseError);
        return new ParsedUdpPayload(record, List.of());
    }

    private ParsedUdpPayload parseQar(DataRecord record, JsonNode root, OffsetDateTime receivedAt) {
        String sourceTime = requiredText(root, "time");
        OffsetDateTime sampleAt = parseQarSampleAt(sourceTime, receivedAt);
        String flightNo = requiredText(root, "FLIGHT NUMBER");
        String origin = airport(root, "ORIGIN");
        String destination = airport(root, "DESTINATION");

        record.setPayloadCount(1);
        record.setSentAt(sampleAt);
        record.setFlightNo(flightNo);
        record.setOrigin(origin);
        record.setDestination(destination);
        markParsed(record, 1, List.of());

        Map<String, Object> row = row(record.getId());
        row.put("sampleAt", sampleAt);
        row.put("sourceTimeText", sourceTime);
        row.put("flightNo", flightNo);
        row.put("origin", origin);
        row.put("destination", destination);
        row.put("airGroundStatus", requiredText(root, "AIR GND ON GND"));
        row.put("altitudeFt", decimalOrNull(root, "BARO COR ALT NO. 1"));
        row.put("computedAirSpeedKt", decimalOrNull(root, "COMPUTED AIRSPEED"));
        row.put("groundSpeedKt", decimalOrNull(root, "GROUNDSPEED"));
        row.put("latitude", doubleOrNull(root, "PRES POSN LAT - FMC"));
        row.put("longitude", doubleOrNull(root, "PRES POSN LONG - FMC"));
        row.put("trackAngleDeg", decimalOrNull(root, "TRACK ANGLE TRUE - FMC"));
        row.put("headingDeg", decimalOrNull(root, "CAPT DISPLAY HEADING"));
        row.put("pitchDeg", decimalOrNull(root, "BODY PITCH RATE"));
        row.put("rollDeg", decimalOrNull(root, "BODY ROLL RATE"));
        row.put("leftFuelQty", decimalOrNull(root, "LT MAIN FUEL QTY"));
        row.put("rightFuelQty", decimalOrNull(root, "RT MAIN FUEL QTY"));
        row.put("centerFuelQty", decimalOrNull(root, "CENTER MAIN FUEL QTY"));
        row.put("lowFuelWarning", booleanOrNull(root, "LOW FUEL QTY TANK1/2"));
        row.put("distanceToGoNm", decimalOrNull(root, "DISTANCE TO GO"));
        row.put("destinationEtaText", textOrNull(root, "DESTINATION ETA"));
        row.put("frameCount", longValue(root, "frameCount"));
        return new ParsedUdpPayload(record, List.of(row));
    }

    private ParsedUdpPayload parseGroundTask(DataRecord record, JsonNode root) {
        verifyMessageType(root, "ground.task");
        JsonNode payload = requiredObject(root, "payload");
        OffsetDateTime snapshotAt = requiredOffsetTime(root, "sentAt");
        String flightNo = requiredText(payload, "flightNo");

        record.setPayloadCount(1);
        record.setSentAt(snapshotAt);
        record.setFlightNo(flightNo);
        markParsed(record, 1, List.of());

        Map<String, Object> row = row(record.getId());
        row.put("taskId", requiredText(payload, "taskId"));
        row.put("flightNo", flightNo);
        row.put("scenarioName", requiredText(payload, "scenarioName"));
        row.put("status", requiredText(payload, "status"));
        row.put("phase", textOrNull(payload, "phase"));
        row.put("terminalCount", intValue(payload, "terminalCount"));
        row.put("startedAt", requiredOffsetTime(payload, "startedAt"));
        row.put("endedAt", offsetTimeOrNull(payload, "endedAt"));
        row.put("downlinkTargetMbps", decimalOrNull(payload, "downlinkTargetMbps"));
        row.put("statisticsWindowSeconds", intValue(payload, "statisticsWindowSeconds"));
        row.put("totalBytes", longValue(payload, "totalBytes"));
        row.put("failureReason", textOrNull(payload, "failureReason"));
        row.put("rerunSourceTaskId", textOrNull(payload, "rerunSourceTaskId"));
        row.put("archived", booleanValue(payload, "archived"));
        row.put("snapshotAt", snapshotAt);
        return new ParsedUdpPayload(record, List.of(row));
    }

    private ParsedUdpPayload parseTraffic(DataRecord record, JsonNode root) {
        verifyMessageType(root, "ground.traffic_record");
        OffsetDateTime sentAt = requiredOffsetTime(root, "sentAt");
        ArrayNode items = requiredArray(root, "items");
        record.setSentAt(sentAt);
        record.setPayloadCount(Math.max(1, items.size()));

        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            try {
                JsonNode item = items.get(index);
                Map<String, Object> row = row(record.getId(), index);
                row.put("taskId", requiredText(item, "taskId"));
                row.put("windowStart", requiredOffsetTime(item, "windowStart"));
                row.put("windowEnd", requiredOffsetTime(item, "windowEnd"));
                row.put("terminalId", requiredText(item, "terminalId"));
                row.put("displayTerminalId", textOrNull(item, "displayTerminalId"));
                row.put("seatLabel", textOrNull(item, "seatLabel"));
                row.put("application", requiredText(item, "application"));
                row.put("protocol", requiredText(item, "protocol"));
                row.put("direction", requiredText(item, "direction"));
                row.put("bytesCount", longValue(item, "bytesCount"));
                row.put("packetCount", longValue(item, "packetCount"));
                row.put("throughputMbps", decimalValue(item, "throughputMbps"));
                row.put("peakMbps", decimalValue(item, "peakMbps"));
                row.put("recordStatus", requiredText(item, "recordStatus"));
                rows.add(row);
            } catch (RuntimeException exception) {
                errors.add(itemError(index, exception));
            }
        }
        record.setFlightNo(flightNoFromRows(rows));
        markParsed(record, rows.size(), errors);
        return new ParsedUdpPayload(record, rows);
    }

    private ParsedUdpPayload parseSession(DataRecord record, JsonNode root) {
        verifyMessageType(root, "ground.session_summary");
        OffsetDateTime snapshotAt = requiredOffsetTime(root, "sentAt");
        ArrayNode items = requiredArray(root, "items");
        record.setSentAt(snapshotAt);
        record.setPayloadCount(Math.max(1, items.size()));

        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            try {
                JsonNode item = items.get(index);
                Map<String, Object> row = row(record.getId(), index);
                row.put("sessionId", requiredText(item, "sessionId"));
                row.put("taskId", requiredText(item, "taskId"));
                row.put("terminalId", requiredText(item, "terminalId"));
                row.put("displayTerminalId", textOrNull(item, "displayTerminalId"));
                row.put("seatLabel", textOrNull(item, "seatLabel"));
                row.put("application", requiredText(item, "application"));
                row.put("protocol", requiredText(item, "protocol"));
                row.put("startedAt", requiredOffsetTime(item, "startedAt"));
                row.put("durationSeconds", longValue(item, "durationSeconds"));
                row.put("uplinkBytes", longValue(item, "uplinkBytes"));
                row.put("downlinkBytes", longValue(item, "downlinkBytes"));
                row.put("averageThroughputMbps", decimalValue(item, "averageThroughputMbps"));
                row.put("peakThroughputMbps", decimalValue(item, "peakThroughputMbps"));
                row.put("status", requiredText(item, "status"));
                row.put("snapshotAt", snapshotAt);
                rows.add(row);
            } catch (RuntimeException exception) {
                errors.add(itemError(index, exception));
            }
        }
        record.setFlightNo(flightNoFromRows(rows));
        markParsed(record, rows.size(), errors);
        return new ParsedUdpPayload(record, rows);
    }

    private ParsedUdpPayload parseSmartWindow(DataRecord record, JsonNode root) {
        verifyMessageType(root, "smart_window.status");
        ArrayNode items = requiredArray(root, "items");
        record.setPayloadCount(Math.max(1, items.size()));

        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            try {
                JsonNode item = items.get(index);
                Map<String, Object> row = row(record.getId(), index);
                row.put("windowId", intValue(item, "windowId"));
                row.put("zoneId", intValue(item, "zoneId"));
                row.put("brightnessLevel", intValue(item, "brightnessLevel"));
                row.put("connectStatus", booleanValue(item, "connectStatus"));
                row.put("status", requiredText(item, "status"));
                row.put("eventAt", requiredCompactTime(item, "timestamp"));
                rows.add(row);
            } catch (RuntimeException exception) {
                errors.add(itemError(index, exception));
            }
        }
        markParsed(record, rows.size(), errors);
        return new ParsedUdpPayload(record, rows);
    }

    private ParsedUdpPayload parseIfe(DataRecord record, JsonNode root, boolean cockrell) {
        verifyMessageType(root, cockrell ? "ife_cockrell.behavior" : "ife_633.behavior");
        ArrayNode items = requiredArray(root, "items");
        record.setPayloadCount(Math.max(1, items.size()));

        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            try {
                JsonNode item = items.get(index);
                JsonNode sysInfo = requiredObject(item, "sysInfo");
                JsonNode paxInfo = requiredObject(item, "paxInfo");
                ObjectNode behaviorInfo = objectCopy(requiredObject(item, "behaviorInfo"));
                JsonNode extInfo = item.path("extInfo");

                CoverInfo coverInfo = cockrell ? extractCoverInfo(behaviorInfo) : CoverInfo.empty();
                removeCoverBase64(behaviorInfo);
                if (cockrell) {
                    removeCoverMetadata(behaviorInfo);
                }

                Map<String, Object> row = row(record.getId(), index);
                row.put("eventAt", requiredCompactTime(sysInfo, "timestamp"));
                row.put("flightNo", requiredText(sysInfo, "flightId"));
                row.put("pnr", requiredText(paxInfo, "pnr"));
                row.put("seatNo", requiredText(paxInfo, "seatNo"));
                row.put("cabinClass", requiredText(paxInfo, "cabinClass"));
                row.put("deviceId", requiredText(paxInfo, "deviceId"));
                row.put("passengerId", requiredText(paxInfo, "userId"));
                row.put("behaviorType", requiredText(behaviorInfo, "behaviorType"));
                row.put("behaviorDetail", toJson(behaviorInfo));
                row.put("errorCode", textOrNull(extInfo, "errorCode"));
                row.put("errorDescription", textOrNull(extInfo, "errorDescription"));
                if (cockrell) {
                    row.put("coverMimeType", coverInfo.mimeType());
                    row.put("coverChecksum", coverInfo.checksum());
                }
                rows.add(row);
            } catch (RuntimeException exception) {
                errors.add(itemError(index, exception));
            }
        }
        record.setFlightNo(firstString(rows, "flightNo"));
        markParsed(record, rows.size(), errors);
        return new ParsedUdpPayload(record, rows);
    }

    private DataRecord baseRecord(
            DataTypeConfig config,
            OffsetDateTime receivedAt,
            String sourceHost,
            Integer sourcePort
    ) {
        DataRecord record = new DataRecord();
        record.setId(UUID.randomUUID());
        record.setDataTypeCode(config.getCode());
        record.setSourceSystemCode(config.getSourceSystemCode());
        record.setSourceDeviceCode(config.getSourceDeviceCode());
        record.setSourceHost(sourceHost);
        record.setSourcePort(sourcePort);
        record.setAircraftRegistrationNo(properties.aircraftRegistrationNo());
        record.setAircraftModel(properties.aircraftModel());
        record.setAirlineCode(properties.airlineCode());
        record.setSentAt(receivedAt);
        record.setReceivedAt(receivedAt);
        record.setPayloadCount(1);
        record.setParseStatus("RECEIVED");
        return record;
    }

    private void markParsed(DataRecord record, int businessRowCount, List<String> errors) {
        if (businessRowCount == 0) {
            record.setParseStatus("FAILED");
            record.setParseError(errors.isEmpty() ? "No business rows parsed" : joinedErrors(errors));
            return;
        }
        if (errors.isEmpty()) {
            record.setParseStatus("PARSED");
            record.setParseError(null);
            return;
        }
        record.setParseStatus("PARTIAL");
        record.setParseError(joinedErrors(errors));
    }

    private void markFailed(DataRecord record, String parseError) {
        record.setParseStatus("FAILED");
        record.setParseError(parseError);
        record.setPayloadCount(record.getPayloadCount() == null ? 1 : record.getPayloadCount());
    }

    private void enrichAirline(DataRecord record) {
        String flightNo = record.getFlightNo();
        if (flightNo != null && flightNo.length() >= 2 && Character.isLetter(flightNo.charAt(0))
                && Character.isLetter(flightNo.charAt(1))) {
            record.setAirlineCode(flightNo.substring(0, 2).toUpperCase(Locale.ROOT));
        }
    }

    private OffsetDateTime parseRootSentAt(JsonNode root, OffsetDateTime receivedAt) {
        String sentAt = textOrNull(root, "sentAt");
        return sentAt == null ? receivedAt : parseOffsetTime(sentAt, "sentAt");
    }

    private void verifyMessageType(JsonNode root, String expected) {
        String actual = textOrNull(root, "messageType");
        if (actual != null && !expected.equals(actual)) {
            throw new PayloadParseException("messageType expected " + expected + " but was " + actual);
        }
    }

    private OffsetDateTime parseQarSampleAt(String text, OffsetDateTime receivedAt) {
        try {
            LocalTime localTime = LocalTime.parse(text);
            LocalDate date = receivedAt.atZoneSameInstant(properties.zone()).toLocalDate();
            return LocalDateTime.of(date, localTime).atZone(properties.zone()).toOffsetDateTime();
        } catch (DateTimeParseException exception) {
            throw new PayloadParseException("invalid QAR time", exception);
        }
    }

    private OffsetDateTime requiredCompactTime(JsonNode node, String field) {
        return parseCompactTime(requiredText(node, field), field);
    }

    private OffsetDateTime parseCompactTime(String text, String field) {
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(text, COMPACT_TIME);
            return localDateTime.atZone(properties.zone()).toOffsetDateTime();
        } catch (DateTimeParseException exception) {
            throw new PayloadParseException("invalid compact time " + field, exception);
        }
    }

    private OffsetDateTime requiredOffsetTime(JsonNode node, String field) {
        return parseOffsetTime(requiredText(node, field), field);
    }

    private OffsetDateTime offsetTimeOrNull(JsonNode node, String field) {
        String text = textOrNull(node, field);
        return text == null ? null : parseOffsetTime(text, field);
    }

    private OffsetDateTime parseOffsetTime(String text, String field) {
        try {
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException exception) {
            try {
                ZoneId zone = properties.zone();
                return LocalDateTime.parse(text).atZone(zone).toOffsetDateTime();
            } catch (DateTimeParseException ignored) {
                throw new PayloadParseException("invalid offset time " + field, exception);
            }
        }
    }

    private JsonNode requiredObject(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isObject()) {
            throw new PayloadParseException("missing object field " + field);
        }
        return value;
    }

    private ArrayNode requiredArray(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            throw new PayloadParseException("missing array field " + field);
        }
        return (ArrayNode) value;
    }

    private String airport(JsonNode node, String field) {
        String value = requiredText(node, field).toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9]{4}")) {
            throw new PayloadParseException("invalid airport " + field);
        }
        return value;
    }

    private String requiredText(JsonNode node, String field) {
        String value = textOrNull(node, field);
        if (value == null) {
            throw new PayloadParseException("missing text field " + field);
        }
        return value;
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.isTextual() ? value.asText() : value.asText(null);
        return text == null || text.isBlank() ? null : text.trim();
    }

    private BigDecimal decimalValue(JsonNode node, String field) {
        BigDecimal value = decimalOrNull(node, field);
        if (value == null) {
            throw new PayloadParseException("missing decimal field " + field);
        }
        return value;
    }

    private BigDecimal decimalOrNull(JsonNode node, String field) {
        String text = textOrNull(node, field);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            throw new PayloadParseException("invalid decimal field " + field, exception);
        }
    }

    private Double doubleOrNull(JsonNode node, String field) {
        String text = textOrNull(node, field);
        if (text == null) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            throw new PayloadParseException("invalid double field " + field, exception);
        }
    }

    private int intValue(JsonNode node, String field) {
        String text = requiredText(node, field);
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            throw new PayloadParseException("invalid integer field " + field, exception);
        }
    }

    private long longValue(JsonNode node, String field) {
        String text = requiredText(node, field);
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            throw new PayloadParseException("invalid long field " + field, exception);
        }
    }

    private boolean booleanValue(JsonNode node, String field) {
        Boolean value = booleanOrNull(node, field);
        if (value == null) {
            throw new PayloadParseException("missing boolean field " + field);
        }
        return value;
    }

    private Boolean booleanOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        String text = value.asText(null);
        if (text == null || text.isBlank()) {
            return null;
        }
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        throw new PayloadParseException("invalid boolean field " + field);
    }

    private Map<String, Object> row(UUID recordId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("recordId", recordId);
        return row;
    }

    private Map<String, Object> row(UUID recordId, int zeroBasedIndex) {
        Map<String, Object> row = row(recordId);
        row.put("itemNo", zeroBasedIndex + 1);
        return row;
    }

    private String flightNoFromRows(List<Map<String, Object>> rows) {
        String taskId = firstString(rows, "taskId");
        if (taskId == null) {
            return null;
        }
        int separator = taskId.indexOf("-FLIGHT-");
        return separator > 0 ? taskId.substring(0, separator) : null;
    }

    private String firstString(List<Map<String, Object>> rows, String key) {
        for (Map<String, Object> row : rows) {
            Object value = row.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private ObjectNode objectCopy(JsonNode node) {
        return (ObjectNode) node.deepCopy();
    }

    private CoverInfo extractCoverInfo(ObjectNode node) {
        CoverInfo found = findCoverInfo(node);
        return found == null ? CoverInfo.empty() : found;
    }

    private CoverInfo findCoverInfo(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            String mimeType = textOrNull(node, "coverMimeType");
            String checksum = textOrNull(node, "coverChecksum");
            String base64 = textOrNull(node, "coverBase64");
            if (mimeType != null || checksum != null || base64 != null) {
                return new CoverInfo(mimeType, checksum == null && base64 != null ? sha256(base64) : checksum);
            }
            for (JsonNode child : node) {
                CoverInfo found = findCoverInfo(child);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                CoverInfo found = findCoverInfo(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void removeCoverBase64(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            objectNode.remove("coverBase64");
            objectNode.forEach(this::removeCoverBase64);
            return;
        }
        if (node.isArray()) {
            node.forEach(this::removeCoverBase64);
        }
    }

    private void removeCoverMetadata(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            objectNode.remove("coverMimeType");
            objectNode.remove("coverChecksum");
            objectNode.forEach(this::removeCoverMetadata);
            return;
        }
        if (node.isArray()) {
            node.forEach(this::removeCoverMetadata);
        }
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new PayloadParseException("behavior detail serialization failed", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String joinedErrors(List<String> errors) {
        return String.join("; ", errors.stream().limit(5).toList());
    }

    private String itemError(int index, RuntimeException exception) {
        return "item " + (index + 1) + ": " + summarize(exception);
    }

    private String summarize(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            message = root.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    public static String invalidUtf8Summary(byte[] bytes, int length) {
        int prefixLength = Math.min(length, 64);
        byte[] prefix = new byte[prefixLength];
        System.arraycopy(bytes, 0, prefix, 0, prefixLength);
        return "INVALID_UTF8 length=" + length
                + " prefixBase64=" + Base64.getEncoder().encodeToString(prefix);
    }

    private record CoverInfo(String mimeType, String checksum) {
        static CoverInfo empty() {
            return new CoverInfo(null, null);
        }
    }

    private static class PayloadParseException extends RuntimeException {
        PayloadParseException(String message) {
            super(message);
        }

        PayloadParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


