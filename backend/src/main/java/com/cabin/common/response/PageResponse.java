package com.cabin.common.response;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        int pageSize,
        long total,
        long totalPages
) {
    public PageResponse {
        items = items == null ? List.of() : List.copyOf(items);
        if (page < 1) {
            throw new IllegalArgumentException("page must be greater than or equal to 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be greater than or equal to 1");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must be greater than or equal to 0");
        }
        totalPages = (total + pageSize - 1) / pageSize;
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int pageSize, long total) {
        return new PageResponse<>(items, page, pageSize, total, 0);
    }
}
