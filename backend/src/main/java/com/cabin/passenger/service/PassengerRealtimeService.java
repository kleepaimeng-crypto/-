package com.cabin.passenger.service;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ResponseCode;
import com.cabin.passenger.dto.MediaRankResponse;
import com.cabin.passenger.dto.MediaStatisticsResponse;
import com.cabin.passenger.dto.PassengerActivitiesResponse;
import com.cabin.passenger.dto.PassengerActivityResponse;
import com.cabin.passenger.dto.PassengerRealtimeSnapshotResponse;
import com.cabin.passenger.entity.PassengerActivityRow;
import com.cabin.passenger.mapper.PassengerRealtimeMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PassengerRealtimeService {
    private final ObjectProvider<PassengerRealtimeMapper> mapperProvider;

    public PassengerRealtimeService(ObjectProvider<PassengerRealtimeMapper> mapperProvider) {
        this.mapperProvider = mapperProvider;
    }

    @Transactional(readOnly = true)
    public PassengerRealtimeSnapshotResponse getSnapshot() {
        PassengerRealtimeMapper mapper = mapper();
        String flightNo = mapper.findCurrentFlightNo();
        if (flightNo == null || flightNo.isBlank()) {
            return emptySnapshot();
        }

        List<PassengerActivityRow> rows = mapper.findLatestActivities(flightNo);
        List<MediaRankResponse> videoRanking = rank(mapper.findLatestMediaTypes(flightNo, "MOVIE_PLAY"));
        List<MediaRankResponse> musicRanking = rank(mapper.findLatestMediaTypes(flightNo, "MUSIC_PLAY"));
        MediaStatisticsResponse media = new MediaStatisticsResponse(
                total(videoRanking), videoRanking, total(musicRanking), musicRanking
        );
        Map<String, PassengerActivityRow> rowsBySeat = rows.stream()
                .filter(row -> row.getSeatNo() != null)
                .collect(Collectors.toMap(
                        PassengerActivityRow::getSeatNo,
                        Function.identity(),
                        (left, right) -> left.getEventAt().isAfter(right.getEventAt()) ? left : right,
                        LinkedHashMap::new
                ));
        List<PassengerActivityResponse> activities = A330SeatManifest.seats().stream()
                .map(seat -> toActivity(seat, rowsBySeat.get(seat.seatNo())))
                .toList();
        OffsetDateTime updatedAt = rows.stream()
                .map(PassengerActivityRow::getEventAt)
                .filter(value -> value != null)
                .max(OffsetDateTime::compareTo)
                .orElse(null);

        return new PassengerRealtimeSnapshotResponse(
                true,
                updatedAt,
                media,
                new PassengerActivitiesResponse(activities.size(), activities)
        );
    }

    private PassengerRealtimeSnapshotResponse emptySnapshot() {
        List<PassengerActivityResponse> activities = A330SeatManifest.seats().stream()
                .map(seat -> toActivity(seat, null))
                .toList();
        return new PassengerRealtimeSnapshotResponse(
                false,
                null,
                new MediaStatisticsResponse(0, List.of(), 0, List.of()),
                new PassengerActivitiesResponse(activities.size(), activities)
        );
    }

    private PassengerActivityResponse toActivity(A330SeatManifest.Seat seat, PassengerActivityRow row) {
        if (row == null) {
            return new PassengerActivityResponse(
                    null, seat.seatNo(), seat.cabinClass(), null, "IDLE", null, List.of(),
                    null, null, null, null, null, null, null, null, null
            );
        }
        return new PassengerActivityResponse(
                row.getPassengerId(),
                seat.seatNo(),
                row.getCabinClass(),
                row.getBehaviorType(),
                activityKind(row.getBehaviorType()),
                row.getTitle(),
                splitTypes(row.getTypesText()),
                row.getAction(),
                row.getDomain(),
                row.getUrl(),
                row.getTrafficBytes(),
                row.getBandwidthMbps(),
                row.getWindowBytes(),
                row.getEventAt(),
                row.getBandwidthUpdatedAt(),
                row.getSourceRecordId()
        );
    }

    private String activityKind(String behaviorType) {
        if (behaviorType == null) {
            return "IDLE";
        }
        return switch (behaviorType) {
            case "MOVIE_PLAY" -> "VIDEO";
            case "MUSIC_PLAY" -> "MUSIC";
            case "WAP_BROWSING" -> "BROWSING";
            default -> "OTHER";
        };
    }

    private List<MediaRankResponse> rank(List<String> values) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String value : values) {
            for (String type : splitTypes(value)) {
                counts.merge(type, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new MediaRankResponse(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(MediaRankResponse::count).reversed()
                        .thenComparing(MediaRankResponse::type))
                .toList();
    }

    private int total(List<MediaRankResponse> ranking) {
        return ranking.stream().mapToInt(MediaRankResponse::count).sum();
    }

    private List<String> splitTypes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> types = Arrays.stream(value.split("/"))
                .map(String::trim)
                .filter(type -> !type.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ArrayList<>(types);
    }

    private PassengerRealtimeMapper mapper() {
        PassengerRealtimeMapper mapper = mapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
        }
        return mapper;
    }
}
