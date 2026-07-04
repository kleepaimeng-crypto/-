package com.cabin.common.response;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        int pageSize,
        long total,
        int totalPages
) {
    public static <T> PageResponse<T> of(List<T> items, int page, int pageSize, long total) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be greater than 0");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be greater than 0");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return new PageResponse<>(List.copyOf(items), page, pageSize, total, totalPages);
    }
}
