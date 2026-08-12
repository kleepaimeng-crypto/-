package com.cabin.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SeatNumberNormalizerTests {
    @Test
    void acceptsLetterFirstAndNumberFirstFormats() {
        assertThat(SeatNumberNormalizer.normalize("A11")).isEqualTo("A11");
        assertThat(SeatNumberNormalizer.normalize("11A")).isEqualTo("A11");
        assertThat(SeatNumberNormalizer.normalize(" 31k ")).isEqualTo("K31");
        assertThat(SeatNumberNormalizer.normalize(null)).isNull();
    }
}
