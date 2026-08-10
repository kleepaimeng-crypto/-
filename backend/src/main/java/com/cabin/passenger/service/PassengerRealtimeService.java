package com.cabin.passenger.service;

import com.cabin.common.exception.BusinessException;
import com.cabin.common.response.ResponseCode;
import com.cabin.passenger.dto.EntertainmentRecommendationResponse;
import com.cabin.passenger.dto.EntertainmentWorkResponse;
import com.cabin.passenger.dto.MediaRankResponse;
import com.cabin.passenger.dto.MediaStatisticsResponse;
import com.cabin.passenger.dto.PassengerActivitiesResponse;
import com.cabin.passenger.dto.PassengerActivityResponse;
import com.cabin.passenger.dto.PassengerRealtimeSnapshotResponse;
import com.cabin.passenger.entity.EntertainmentWorkRow;
import com.cabin.passenger.entity.PassengerActivityRow;
import com.cabin.passenger.mapper.EntertainmentWorkMapper;
import com.cabin.udp.dto.CurrentFlightContext;
import com.cabin.udp.service.CurrentFlightContextService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class PassengerRealtimeService {
    private static final int RECOMMENDATION_LIMIT = 3;

    private final ObjectProvider<EntertainmentWorkMapper> workMapperProvider;
    private final CurrentFlightContextService currentFlightContextService;
    private final IfeCockrellCurrentStateCache cockrellStateCache;

    public PassengerRealtimeService(
            ObjectProvider<EntertainmentWorkMapper> workMapperProvider,
            CurrentFlightContextService currentFlightContextService,
            IfeCockrellCurrentStateCache cockrellStateCache
    ) {
        this.workMapperProvider = workMapperProvider;
        this.currentFlightContextService = currentFlightContextService;
        this.cockrellStateCache = cockrellStateCache;
    }

    public PassengerRealtimeSnapshotResponse getSnapshot() {
        CurrentFlightContext context = currentFlightContextService.current();
        if (context == null || context.flightSessionId() == null) {
            return emptySnapshot();
        }

        List<PassengerActivityRow> rows = cockrellStateCache.snapshot(context.flightSessionId());
        if (rows.isEmpty()) {
            return emptySnapshot();
        }
        List<EntertainmentWorkRow> works = workMapper().findEnabledWorks();
        Map<String, EntertainmentWorkRow> worksByCode = works.stream()
                .collect(Collectors.toMap(
                        EntertainmentWorkRow::getWorkCode,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, EntertainmentWorkRow> worksByCategoryTitle = works.stream()
                .collect(Collectors.toMap(
                        work -> catalogTitleKey(work.getCategory(), work.getTitle()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<PassengerActivityRow, EntertainmentWorkRow> resolvedWorks = new IdentityHashMap<>();
        for (PassengerActivityRow row : rows) {
            EntertainmentWorkRow work = resolveWork(row, worksByCode, worksByCategoryTitle);
            if (work != null) {
                resolvedWorks.put(row, work);
            }
        }

        List<PassengerActivityRow> currentVideoRows = currentRows(rows, "MOVIE_PLAY");
        List<PassengerActivityRow> currentMusicRows = currentRows(rows, "MUSIC_PLAY");
        Map<String, Integer> viewerCounts = currentViewerCounts(rows, resolvedWorks);
        MediaStatisticsResponse media = new MediaStatisticsResponse(
                currentPassengerCount(currentVideoRows),
                rank(currentVideoRows, resolvedWorks),
                currentPassengerCount(currentMusicRows),
                rank(currentMusicRows, resolvedWorks)
        );
        Map<String, PassengerActivityRow> rowsBySeat = rows.stream()
                .filter(row -> row.getSeatNo() != null)
                .collect(Collectors.toMap(
                        PassengerActivityRow::getSeatNo,
                        Function.identity(),
                        (left, right) -> laterRow(left, right),
                        LinkedHashMap::new
                ));
        List<PassengerActivityResponse> activities = C929SeatManifest.seats().stream()
                .map(seat -> toActivity(
                        seat,
                        rowsBySeat.get(seat.seatNo()),
                        works,
                        resolvedWorks,
                        viewerCounts
                ))
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
        List<PassengerActivityResponse> activities = C929SeatManifest.seats().stream()
                .map(seat -> toActivity(seat, null, List.of(), Map.of(), Map.of()))
                .toList();
        return new PassengerRealtimeSnapshotResponse(
                false,
                null,
                new MediaStatisticsResponse(0, List.of(), 0, List.of()),
                new PassengerActivitiesResponse(activities.size(), activities)
        );
    }

    private PassengerActivityResponse toActivity(
            C929SeatManifest.Seat seat,
            PassengerActivityRow row,
            List<EntertainmentWorkRow> works,
            Map<PassengerActivityRow, EntertainmentWorkRow> resolvedWorks,
            Map<String, Integer> viewerCounts
    ) {
        if (row == null) {
            return new PassengerActivityResponse(
                    null, seat.seatNo(), seat.cabinClass(), null, null, null, List.of(),
                    null, null, null, null, null, null, null, null, null,
                    null, List.of()
            );
        }

        EntertainmentWorkRow work = resolvedWorks.get(row);
        List<String> types = work == null ? splitTypes(row.getTypesText()) : splitTypes(work.getGenresText());
        String title = work == null ? row.getTitle() : work.getTitle();
        return new PassengerActivityResponse(
                row.getPassengerId(),
                seat.seatNo(),
                row.getCabinClass(),
                row.getBehaviorType(),
                activityKind(row.getBehaviorType()),
                title,
                types,
                row.getAction(),
                row.getDomain(),
                row.getUrl(),
                row.getTrafficBytes(),
                row.getBandwidthMbps(),
                row.getWindowBytes(),
                row.getEventAt(),
                row.getBandwidthUpdatedAt(),
                row.getSourceRecordId(),
                toWorkResponse(work),
                recommendations(row, work, types, works, viewerCounts)
        );
    }

    private List<EntertainmentRecommendationResponse> recommendations(
            PassengerActivityRow row,
            EntertainmentWorkRow currentWork,
            List<String> currentTypes,
            List<EntertainmentWorkRow> works,
            Map<String, Integer> viewerCounts
    ) {
        String category = mediaCategory(row.getBehaviorType());
        if (category == null) {
            return List.of();
        }

        Set<String> typeSet = new LinkedHashSet<>(currentTypes);
        Comparator<EntertainmentWorkRow> popularityOrder = Comparator
                .comparingInt((EntertainmentWorkRow work) ->
                        viewerCounts.getOrDefault(work.getWorkCode(), 0))
                .reversed()
                .thenComparingInt(work -> work.getSortOrder() == null
                        ? Integer.MAX_VALUE
                        : work.getSortOrder())
                .thenComparing(EntertainmentWorkRow::getWorkCode);
        List<EntertainmentWorkRow> categoryWorks = works.stream()
                .filter(work -> category.equals(work.getCategory()))
                .filter(work -> !isCurrentWork(work, currentWork, row.getTitle()))
                .sorted(popularityOrder)
                .toList();
        List<EntertainmentRecommendationResponse> result = new ArrayList<>(RECOMMENDATION_LIMIT);
        Set<String> selectedCodes = new LinkedHashSet<>();

        for (EntertainmentWorkRow candidate : categoryWorks) {
            if (typeSet.isEmpty() || !hasSharedType(typeSet, candidate)) {
                continue;
            }
            result.add(toRecommendation(candidate, viewerCounts, "SAME_TYPE"));
            selectedCodes.add(candidate.getWorkCode());
            if (result.size() == RECOMMENDATION_LIMIT) {
                return List.copyOf(result);
            }
        }

        for (EntertainmentWorkRow candidate : categoryWorks) {
            if (selectedCodes.contains(candidate.getWorkCode())) {
                continue;
            }
            result.add(toRecommendation(candidate, viewerCounts, "CATEGORY_POPULAR"));
            if (result.size() == RECOMMENDATION_LIMIT) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private EntertainmentRecommendationResponse toRecommendation(
            EntertainmentWorkRow work,
            Map<String, Integer> viewerCounts,
            String reason
    ) {
        return new EntertainmentRecommendationResponse(
                work.getWorkCode(),
                work.getCategory(),
                work.getTitle(),
                splitTypes(work.getGenresText()),
                work.getCreatorName(),
                viewerCounts.getOrDefault(work.getWorkCode(), 0),
                reason
        );
    }

    private EntertainmentWorkResponse toWorkResponse(EntertainmentWorkRow work) {
        if (work == null) {
            return null;
        }
        return new EntertainmentWorkResponse(
                work.getWorkCode(),
                work.getCategory(),
                work.getTitle(),
                splitTypes(work.getGenresText()),
                work.getSummary(),
                work.getCreatorName(),
                work.getCollectionName(),
                work.getDurationSeconds(),
                work.getReleaseYear(),
                work.getLanguage(),
                work.getRegion()
        );
    }

    private boolean hasSharedType(Set<String> currentTypes, EntertainmentWorkRow candidate) {
        return splitTypes(candidate.getGenresText()).stream().anyMatch(currentTypes::contains);
    }

    private boolean isCurrentWork(
            EntertainmentWorkRow candidate,
            EntertainmentWorkRow currentWork,
            String currentTitle
    ) {
        if (currentWork != null) {
            return currentWork.getWorkCode().equals(candidate.getWorkCode());
        }
        return normalizedTitle(candidate.getTitle()).equals(normalizedTitle(currentTitle));
    }

    private EntertainmentWorkRow resolveWork(
            PassengerActivityRow row,
            Map<String, EntertainmentWorkRow> worksByCode,
            Map<String, EntertainmentWorkRow> worksByCategoryTitle
    ) {
        String category = mediaCategory(row.getBehaviorType());
        if (category == null) {
            return null;
        }

        String mediaCode = row.getMediaCode();
        if (mediaCode != null && !mediaCode.isBlank()) {
            EntertainmentWorkRow byCode = worksByCode.get(mediaCode.trim());
            if (byCode != null
                    && category.equals(byCode.getCategory())
                    && normalizedTitle(byCode.getTitle()).equals(normalizedTitle(row.getTitle()))) {
                return byCode;
            }
        }
        return worksByCategoryTitle.get(catalogTitleKey(category, row.getTitle()));
    }

    private Map<String, Integer> currentViewerCounts(
            List<PassengerActivityRow> rows,
            Map<PassengerActivityRow, EntertainmentWorkRow> resolvedWorks
    ) {
        Map<String, Set<String>> viewers = new HashMap<>();
        for (PassengerActivityRow row : rows) {
            EntertainmentWorkRow work = resolvedWorks.get(row);
            String passengerId = row.getPassengerId();
            if (work == null || passengerId == null || passengerId.isBlank()) {
                continue;
            }
            viewers.computeIfAbsent(work.getWorkCode(), ignored -> new LinkedHashSet<>())
                    .add(passengerId);
        }
        return viewers.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().size()
                ));
    }

    private String activityKind(String behaviorType) {
        if (behaviorType == null) {
            return null;
        }
        return switch (behaviorType) {
            case "MOVIE_PLAY" -> "VIDEO";
            case "MUSIC_PLAY" -> "MUSIC";
            case "WAP_BROWSING" -> "BROWSING";
            case "SHOPPING" -> "SHOPPING";
            default -> "OTHER";
        };
    }

    private String mediaCategory(String behaviorType) {
        if ("MOVIE_PLAY".equals(behaviorType)) {
            return "VIDEO";
        }
        if ("MUSIC_PLAY".equals(behaviorType)) {
            return "MUSIC";
        }
        return null;
    }

    private List<PassengerActivityRow> currentRows(List<PassengerActivityRow> rows, String behaviorType) {
        return rows.stream()
                .filter(row -> behaviorType.equals(row.getBehaviorType()))
                .toList();
    }

    private int currentPassengerCount(List<PassengerActivityRow> rows) {
        return (int) rows.stream()
                .map(PassengerActivityRow::getPassengerId)
                .filter(passengerId -> passengerId != null && !passengerId.isBlank())
                .distinct()
                .count();
    }

    private List<MediaRankResponse> rank(
            List<PassengerActivityRow> rows,
            Map<PassengerActivityRow, EntertainmentWorkRow> resolvedWorks
    ) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PassengerActivityRow row : rows) {
            EntertainmentWorkRow work = resolvedWorks.get(row);
            List<String> types = work == null
                    ? splitTypes(row.getTypesText())
                    : splitTypes(work.getGenresText());
            for (String type : types) {
                counts.merge(type, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new MediaRankResponse(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(MediaRankResponse::count).reversed()
                        .thenComparing(MediaRankResponse::type))
                .toList();
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

    private PassengerActivityRow laterRow(PassengerActivityRow left, PassengerActivityRow right) {
        if (left.getEventAt() == null) {
            return right;
        }
        if (right.getEventAt() == null) {
            return left;
        }
        return left.getEventAt().isAfter(right.getEventAt()) ? left : right;
    }

    private String catalogTitleKey(String category, String title) {
        return category + "\u0000" + normalizedTitle(title);
    }

    private String normalizedTitle(String title) {
        return title == null ? "" : title.trim();
    }

    private EntertainmentWorkMapper workMapper() {
        EntertainmentWorkMapper mapper = workMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new BusinessException(ResponseCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
        }
        return mapper;
    }
}
