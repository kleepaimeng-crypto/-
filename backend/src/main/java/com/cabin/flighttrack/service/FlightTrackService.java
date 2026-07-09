package com.cabin.flighttrack.service;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ResponseCode;
import com.cabin.flighttrack.dto.FlightTrackCurrentResponse;
import com.cabin.flighttrack.dto.FlightTrackInfoResponse;
import com.cabin.flighttrack.dto.FlightTrackPointResponse;
import com.cabin.flighttrack.entity.FlightTrackPointRow;
import com.cabin.flighttrack.mapper.FlightTrackMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlightTrackService {
    private static final int POLL_INTERVAL_SECONDS = 5;
    private static final int FRESHNESS_SECONDS = 300;
    private static final int TRACK_WINDOW_HOURS = 24;
    private static final int MAX_TRACK_POINTS = 720;
    private static final BigDecimal ACTIVE_GROUND_SPEED_KT = new BigDecimal("100");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Map<String, String> AIRPORT_NAMES = Map.of(
            "ZBAA", "北京首都国际机场",
            "ZSPD", "上海浦东国际机场",
            "ZGGG", "广州白云国际机场",
            "ZUUU", "成都双流国际机场",
            "ZSHC", "杭州萧山国际机场"
    );
    private static final Map<String, String> AIRLINE_NAMES = Map.of(
            "CA", "中国国际航空",
            "MU", "中国东方航空",
            "CZ", "中国南方航空",
            "HU", "海南航空",
            "MF", "厦门航空"
    );

    private final ObjectProvider<FlightTrackMapper> mapperProvider;
    private final Clock clock;

    @Autowired
    public FlightTrackService(ObjectProvider<FlightTrackMapper> mapperProvider) {
        this(mapperProvider, Clock.systemDefaultZone());
    }

    FlightTrackService(ObjectProvider<FlightTrackMapper> mapperProvider, Clock clock) {
        this.mapperProvider = mapperProvider;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FlightTrackCurrentResponse getCurrent() {
        FlightTrackMapper mapper = mapper();
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusSeconds(FRESHNESS_SECONDS);
        FlightTrackPointRow latest = mapper.findActiveSessionLatest();
        if (latest == null || !isActive(latest)) {
            return null;
        }
        if (latest.getSampleAt() == null || latest.getSampleAt().isBefore(cutoff)) {
            return null;
        }

        OffsetDateTime windowStart = latest.getSampleAt().minusHours(TRACK_WINDOW_HOURS);
        List<FlightTrackPointResponse> track = sampleTrack(mapper.findTrack(
                latest.getFlightSessionId(),
                windowStart,
                latest.getSampleAt()
        ))
                .stream()
                .map(this::toPoint)
                .toList();
        if (track.isEmpty()) {
            track = List.of(toPoint(latest));
        }
        FlightTrackPointResponse latestPoint = track.getLast();
        return new FlightTrackCurrentResponse(
                toInfo(latest),
                latestPoint,
                track.getFirst().sampleAt(),
                latestPoint.sampleAt(),
                POLL_INTERVAL_SECONDS,
                FRESHNESS_SECONDS,
                track
        );
    }

    private boolean isActive(FlightTrackPointRow row) {
        BigDecimal speed = row.getGroundSpeedKt();
        return speed != null && speed.compareTo(ACTIVE_GROUND_SPEED_KT) > 0;
    }

    private List<FlightTrackPointRow> sampleTrack(List<FlightTrackPointRow> rows) {
        int size = rows.size();
        if (size <= MAX_TRACK_POINTS) {
            return rows;
        }
        return java.util.stream.IntStream.range(0, MAX_TRACK_POINTS)
                .map(index -> Math.round((float) index * (size - 1) / (MAX_TRACK_POINTS - 1)))
                .distinct()
                .mapToObj(rows::get)
                .toList();
    }

    private FlightTrackInfoResponse toInfo(FlightTrackPointRow row) {
        String airlineCode = display(row.getAirlineCode());
        String origin = display(row.getOrigin());
        String destination = display(row.getDestination());
        return new FlightTrackInfoResponse(
                row.getAircraftRegistrationNo(),
                row.getAircraftModel(),
                airlineCode,
                lookup(AIRLINE_NAMES, airlineCode),
                row.getFlightNo(),
                origin,
                lookup(AIRPORT_NAMES, origin),
                destination,
                lookup(AIRPORT_NAMES, destination),
                "飞行中",
                row.getSampleAt()
        );
    }

    private FlightTrackPointResponse toPoint(FlightTrackPointRow row) {
        return new FlightTrackPointResponse(
                row.getSampleAt(),
                sampleTimeText(row),
                row.getFrameCount(),
                row.getLatitude(),
                row.getLongitude(),
                row.getAltitudeFt(),
                row.getGroundSpeedKt(),
                row.getComputedAirSpeedKt(),
                row.getTrackAngleDeg(),
                row.getHeadingDeg(),
                row.getPitchDeg(),
                row.getRollDeg(),
                row.getDistanceToGoNm(),
                row.getDestinationEtaText()
        );
    }

    private String sampleTimeText(FlightTrackPointRow row) {
        if (row.getSourceTimeText() != null && !row.getSourceTimeText().isBlank()) {
            return row.getSourceTimeText();
        }
        return row.getSampleAt() == null ? "" : row.getSampleAt().format(TIME_FORMATTER);
    }

    private String lookup(Map<String, String> names, String code) {
        if (code == null || code.isBlank()) {
            return "未知";
        }
        return names.getOrDefault(code, code);
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }

    private FlightTrackMapper mapper() {
        FlightTrackMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
        }
        return mapper;
    }
}
