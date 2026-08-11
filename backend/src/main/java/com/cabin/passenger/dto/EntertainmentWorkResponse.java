package com.cabin.passenger.dto;

import java.util.List;

public record EntertainmentWorkResponse(
        String workCode,
        String category,
        String title,
        List<String> types,
        String summary,
        String creatorName,
        String collectionName,
        Integer durationSeconds,
        Integer releaseYear,
        String language,
        String region
) {
}
