package com.cabin.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Response<T>(
        String code,
        String message,
        T data,
        List<ErrorDetail> details,
        String traceId
) {
    public static <T> Response<T> success(T data, String traceId) {
        return new Response<>("OK", "success", data, null, traceId);
    }

    public static <T> Response<T> error(
            ResponseCode code,
            String message,
            List<ErrorDetail> details,
            String traceId
    ) {
        return new Response<>(code.name(), message, null, details, traceId);
    }

    public static <T> Response<T> error(ResponseCode code, String message, String traceId) {
        return error(code, message, null, traceId);
    }
}
