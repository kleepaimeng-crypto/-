package com.cabin.flighthistory.service;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.PageResponse;
import com.cabin.common.response.ResponseCode;
import com.cabin.flighthistory.FlightFinishReason;
import com.cabin.flighthistory.dto.FlightHistoryPointResponse;
import com.cabin.flighthistory.dto.FlightHistorySessionResponse;
import com.cabin.flighthistory.dto.FlightHistoryTrackResponse;
import com.cabin.flighthistory.entity.FlightHistoryPointRow;
import com.cabin.flighthistory.entity.FlightHistorySessionRow;
import com.cabin.flighthistory.mapper.FlightHistoryQueryMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "cabin.flight-history.enabled", havingValue = "true", matchIfMissing = true)
public class FlightHistoryQueryService {
    private static final Set<Integer> PAGE_SIZES = Set.of(20, 50, 100);
    private static final Set<String> SORT_FIELDS = Set.of("startedAt", "endedAt", "flightNo", "pointCount");
    private final FlightHistoryQueryMapper mapper;
    private final Clock clock;

    @Autowired
    public FlightHistoryQueryService(FlightHistoryQueryMapper mapper) {
        this(mapper, Clock.systemDefaultZone());
    }

    FlightHistoryQueryService(FlightHistoryQueryMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<FlightHistorySessionResponse> list(
            OffsetDateTime endedFrom, OffsetDateTime endedTo, String flightNo, String origin,
            String destination, String aircraftRegistrationNo, String finishReason,
            int page, int pageSize, String sortBy, String sortDirection
    ) {
        TimeRange range = normalizeRange(endedFrom, endedTo);
        if (page < 1 || !PAGE_SIZES.contains(pageSize)) throw validation("分页参数不正确");
        if (!SORT_FIELDS.contains(sortBy) || !("asc".equalsIgnoreCase(sortDirection) || "desc".equalsIgnoreCase(sortDirection))) {
            throw validation("排序参数不正确");
        }
        String normalizedReason = enumValue(finishReason);
        long total = mapper.countSessions(range.from(), range.to(), prefix(flightNo, 20), airport(origin), airport(destination),
                prefix(aircraftRegistrationNo, 32), normalizedReason);
        List<FlightHistorySessionResponse> items = mapper.findSessions(
                range.from(), range.to(), prefix(flightNo, 20), airport(origin), airport(destination),
                prefix(aircraftRegistrationNo, 32), normalizedReason, sortBy, sortDirection.toLowerCase(Locale.ROOT),
                Math.multiplyExact(page - 1, pageSize), pageSize
        ).stream().map(FlightHistorySessionResponse::from).toList();
        return PageResponse.of(items, page, pageSize, total);
    }

    @Transactional(readOnly = true)
    public FlightHistorySessionResponse get(UUID sessionId) {
        return FlightHistorySessionResponse.from(require(sessionId));
    }

    @Transactional(readOnly = true)
    public FlightHistoryTrackResponse getTrack(UUID sessionId, OffsetDateTime from, OffsetDateTime to, int maxPoints) {
        if (maxPoints < 2 || maxPoints > 3600) throw validation("maxPoints 必须在 2 到 3600 之间");
        FlightHistorySessionRow session = require(sessionId);
        OffsetDateTime rangeFrom = from == null ? session.getStartedAt() : from;
        OffsetDateTime rangeTo = to == null ? session.getEndedAt() : to;
        if (rangeFrom.isBefore(session.getStartedAt()) || rangeTo.isAfter(session.getEndedAt()) || rangeTo.isBefore(rangeFrom)) {
            throw validation("轨迹时间范围必须位于航段内");
        }
        List<FlightHistoryPointRow> source = mapper.findTrack(sessionId, rangeFrom, rangeTo);
        List<FlightHistoryPointRow> returned = sample(source, maxPoints);
        return new FlightHistoryTrackResponse(
                FlightHistorySessionResponse.from(session), rangeFrom, rangeTo, source.size(), returned.size(),
                source.size() > returned.size(), returned.stream().map(FlightHistoryPointResponse::from).toList()
        );
    }

    private TimeRange normalizeRange(OffsetDateTime from, OffsetDateTime to) {
        if (from == null && to == null) throw validation("至少提供一个完成时间范围");
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime normalizedFrom = from == null ? to.minusDays(31) : from;
        OffsetDateTime normalizedTo = to == null ? min(from.plusDays(31), now) : to;
        if (normalizedTo.isBefore(normalizedFrom) || Duration.between(normalizedFrom, normalizedTo).compareTo(Duration.ofDays(31)) > 0) {
            throw validation("完成时间范围不能超过 31 天");
        }
        return new TimeRange(normalizedFrom, normalizedTo);
    }

    private List<FlightHistoryPointRow> sample(List<FlightHistoryPointRow> source, int maxPoints) {
        if (source.size() <= maxPoints) return source;
        return java.util.stream.IntStream.range(0, maxPoints)
                .map(index -> Math.round((float) index * (source.size() - 1) / (maxPoints - 1)))
                .distinct().mapToObj(source::get).toList();
    }

    private FlightHistorySessionRow require(UUID id) {
        FlightHistorySessionRow row = mapper.findSession(id);
        if (row == null) throw new BusinessException(ResponseCode.RESOURCE_NOT_FOUND, "历史航段不存在");
        return row;
    }

    private String prefix(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim().toUpperCase(Locale.ROOT);
        if (result.length() > maxLength) throw validation("筛选条件长度不正确");
        return result;
    }

    private String airport(String value) {
        return prefix(value, 64);
    }

    private String enumValue(String value) {
        if (value == null || value.isBlank()) return null;
        try { return FlightFinishReason.valueOf(value.trim().toUpperCase(Locale.ROOT)).name(); }
        catch (IllegalArgumentException exception) { throw validation("结束原因不正确"); }
    }

    private OffsetDateTime min(OffsetDateTime left, OffsetDateTime right) { return left.isBefore(right) ? left : right; }
    private BusinessException validation(String message) { return new BusinessException(ResponseCode.VALIDATION_ERROR, message); }
    private record TimeRange(OffsetDateTime from, OffsetDateTime to) { }
}
