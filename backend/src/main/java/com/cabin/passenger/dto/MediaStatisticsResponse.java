package com.cabin.passenger.dto;

import java.util.List;

public record MediaStatisticsResponse(
        int videoTotalCount,
        List<MediaRankResponse> videoRanking,
        int musicTotalCount,
        List<MediaRankResponse> musicRanking
) {
}
