package com.cabin.common.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageResponseTests {
    @Test
    void calculatesTotalPages() {
        PageResponse<String> response = PageResponse.of(List.of("a", "b"), 2, 20, 41);

        assertThat(response.items()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.pageSize()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(41);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void rejectsInvalidPagination() {
        assertThatThrownBy(() -> PageResponse.of(List.of(), 0, 20, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageResponse.of(List.of(), 1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageResponse.of(List.of(), 1, 20, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
