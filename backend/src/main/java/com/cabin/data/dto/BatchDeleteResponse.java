package com.cabin.data.dto;

public record BatchDeleteResponse(
        int requested,
        int deleted,
        int skipped
) {
}
