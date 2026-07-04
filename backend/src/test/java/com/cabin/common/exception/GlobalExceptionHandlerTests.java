package com.cabin.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.cabin.common.response.Response;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.trace.TraceContext;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTests {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesBusinessExceptionWithTraceId() {
        MDC.put(TraceContext.TRACE_ID, "trace-business");
        try {
            ResponseEntity<Response<Void>> response = handler.handleBusinessException(
                    new BusinessException(ResponseCode.RESOURCE_CONFLICT, "版本冲突")
            );

            assertThat(response.getStatusCode()).isEqualTo(ResponseCode.RESOURCE_CONFLICT.httpStatus());
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("RESOURCE_CONFLICT");
            assertThat(response.getBody().message()).isEqualTo("版本冲突");
            assertThat(response.getBody().traceId()).isEqualTo("trace-business");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void handlesIllegalArgumentAsValidationError() {
        ResponseEntity<Response<Void>> response = handler.handleBadRequest(new IllegalArgumentException("bad input"));

        assertThat(response.getStatusCode()).isEqualTo(ResponseCode.VALIDATION_ERROR.httpStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).isEqualTo("bad input");
    }
}
