package com.cabin.passenger.dto;

import java.time.OffsetDateTime;

public record PassengerRealtimeSnapshotResponse(
        boolean hasData,
        OffsetDateTime updatedAt,
        MediaStatisticsResponse mediaStatistics,
        PassengerActivitiesResponse passengerActivities
) {
}
