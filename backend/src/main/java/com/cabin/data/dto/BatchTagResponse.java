package com.cabin.data.dto;

public record BatchTagResponse(
        int requestedRecords,
        int requestedTags,
        int changed
) {
}
