package com.cabin.passenger.dto;

import java.util.List;

public record EntertainmentRecommendationResponse(
        String workCode,
        String category,
        String title,
        List<String> types,
        String creatorName,
        int currentViewerCount,
        String reason
) {
}
