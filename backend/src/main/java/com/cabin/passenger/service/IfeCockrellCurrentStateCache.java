package com.cabin.passenger.service;

import com.cabin.passenger.entity.PassengerActivityRow;
import com.cabin.udp.dto.CurrentFlightContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class IfeCockrellCurrentStateCache {
    private static final long SESSION_CLOCK_SKEW_MINUTES = 5;

    private final ObjectMapper objectMapper;
    private final Map<UUID, ConcurrentHashMap<String, PassengerActivityRow>> statesBySession =
            new ConcurrentHashMap<>();

    public IfeCockrellCurrentStateCache(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void update(CurrentFlightContext context, Map<String, Object> row, OffsetDateTime receivedAt) {
        if (!matchesCurrentSession(context, row, receivedAt)) {
            return;
        }
        PassengerActivityRow candidate = toActivity(row, receivedAt);
        statesBySession.computeIfAbsent(context.flightSessionId(), ignored -> new ConcurrentHashMap<>())
                .compute(candidate.getSeatNo(), (seatNo, current) -> later(current, candidate));
    }

    public List<PassengerActivityRow> snapshot(UUID flightSessionId) {
        if (flightSessionId == null) {
            return List.of();
        }
        return statesBySession.getOrDefault(flightSessionId, new ConcurrentHashMap<>()).values().stream()
                .sorted(Comparator.comparing(PassengerActivityRow::getSeatNo))
                .map(this::copy)
                .toList();
    }

    public void retainOnly(UUID activeFlightSessionId) {
        if (activeFlightSessionId == null) {
            statesBySession.clear();
            return;
        }
        statesBySession.keySet().removeIf(sessionId -> !activeFlightSessionId.equals(sessionId));
    }

    private boolean matchesCurrentSession(
            CurrentFlightContext context,
            Map<String, Object> row,
            OffsetDateTime receivedAt
    ) {
        if (context == null || context.flightSessionId() == null || !context.hasRoute()) {
            return false;
        }
        Object flightNo = row.get("flightNo");
        Object eventAt = row.get("eventAt");
        if (!(flightNo instanceof String eventFlightNo) || !(eventAt instanceof OffsetDateTime eventTime)) {
            return false;
        }
        if (!context.flightNo().equalsIgnoreCase(eventFlightNo)) {
            return false;
        }
        OffsetDateTime sessionStartedAt = context.sessionStartedAt();
        return sessionStartedAt == null || !eventTime.isBefore(sessionStartedAt.minusMinutes(SESSION_CLOCK_SKEW_MINUTES));
    }

    private PassengerActivityRow later(PassengerActivityRow current, PassengerActivityRow candidate) {
        if (current == null || current.getEventAt() == null || candidate.getEventAt().isAfter(current.getEventAt())) {
            return candidate;
        }
        if (candidate.getEventAt().isEqual(current.getEventAt())
                && candidate.getBandwidthUpdatedAt().isAfter(current.getBandwidthUpdatedAt())) {
            return candidate;
        }
        return current;
    }

    private PassengerActivityRow toActivity(Map<String, Object> row, OffsetDateTime receivedAt) {
        JsonNode detail = readDetail((String) row.get("behaviorDetail"));
        String behaviorType = (String) row.get("behaviorType");
        PassengerActivityRow activity = new PassengerActivityRow();
        activity.setPassengerId((String) row.get("passengerId"));
        activity.setSeatNo((String) row.get("seatNo"));
        activity.setCabinClass((String) row.get("cabinClass"));
        activity.setBehaviorType(behaviorType);
        activity.setMediaCode(mediaCode(detail, behaviorType));
        activity.setTitle(title(detail, behaviorType));
        activity.setTypesText(types(detail, behaviorType));
        activity.setAction(action(detail, behaviorType));
        activity.setDomain(text(detail, "dstDomain"));
        activity.setUrl(text(detail, "url"));
        activity.setTrafficBytes(longValue(detail, "trafficBytes"));
        activity.setEventAt((OffsetDateTime) row.get("eventAt"));
        activity.setBandwidthUpdatedAt(receivedAt);
        activity.setSourceRecordId((UUID) row.get("recordId"));
        return activity;
    }

    private JsonNode readDetail(String detail) {
        try {
            return objectMapper.readTree(detail);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("invalid Cockrell behavior detail", exception);
        }
    }

    private String mediaCode(JsonNode detail, String behaviorType) {
        return "MOVIE_PLAY".equals(behaviorType) ? text(detail, "contentId")
                : "MUSIC_PLAY".equals(behaviorType) ? text(detail, "musicId") : null;
    }

    private String title(JsonNode detail, String behaviorType) {
        return "MOVIE_PLAY".equals(behaviorType) ? text(detail, "contentName")
                : "MUSIC_PLAY".equals(behaviorType) ? text(detail, "musicName")
                : "WAP_BROWSING".equals(behaviorType) ? text(detail, "dstDomain") : null;
    }

    private String types(JsonNode detail, String behaviorType) {
        return "MOVIE_PLAY".equals(behaviorType) ? text(detail, "contentType")
                : "MUSIC_PLAY".equals(behaviorType) ? text(detail, "musicType") : null;
    }

    private String action(JsonNode detail, String behaviorType) {
        return "MOVIE_PLAY".equals(behaviorType) || "MUSIC_PLAY".equals(behaviorType)
                ? text(detail, "playAction") : null;
    }

    private String text(JsonNode detail, String field) {
        JsonNode value = detail.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private Long longValue(JsonNode detail, String field) {
        JsonNode value = detail.path(field);
        return value.canConvertToLong() ? value.longValue() : null;
    }

    private PassengerActivityRow copy(PassengerActivityRow source) {
        PassengerActivityRow copy = new PassengerActivityRow();
        copy.setPassengerId(source.getPassengerId());
        copy.setSeatNo(source.getSeatNo());
        copy.setCabinClass(source.getCabinClass());
        copy.setBehaviorType(source.getBehaviorType());
        copy.setMediaCode(source.getMediaCode());
        copy.setTitle(source.getTitle());
        copy.setTypesText(source.getTypesText());
        copy.setAction(source.getAction());
        copy.setDomain(source.getDomain());
        copy.setUrl(source.getUrl());
        copy.setTrafficBytes(source.getTrafficBytes());
        copy.setEventAt(source.getEventAt());
        copy.setBandwidthUpdatedAt(source.getBandwidthUpdatedAt());
        copy.setSourceRecordId(source.getSourceRecordId());
        return copy;
    }
}
