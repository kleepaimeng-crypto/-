package com.cabin.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ResponseTests {
    @Test
    void successUsesContractFields() {
        Response<String> response = Response.success("payload", "trace-1");

        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.message()).isEqualTo("success");
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.details()).isNull();
        assertThat(response.traceId()).isEqualTo("trace-1");
    }

    @Test
    void errorUsesExplicitMessageAndDetails() {
        ErrorDetail detail = new ErrorDetail("receivedTo", "invalid_range");

        Response<Void> response = Response.error(
                ResponseCode.VALIDATION_ERROR,
                "receivedTo 必须晚于 receivedFrom",
                List.of(detail),
                "trace-2"
        );

        assertThat(response.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.message()).isEqualTo("receivedTo 必须晚于 receivedFrom");
        assertThat(response.data()).isNull();
        assertThat(response.details()).containsExactly(detail);
        assertThat(response.traceId()).isEqualTo("trace-2");
    }
}
