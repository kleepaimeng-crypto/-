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
        return new Response<>(ResponseCode.OK.name(), ResponseCode.OK.defaultMessage(), data, null, traceId);
    }

    public static <T> Response<T> success(T data) {
        return success(data, null);
    }

    public static Response<Void> success(String traceId) {
        return success(null, traceId);
    }

    public static Response<Void> error(
            ResponseCode responseCode,
            String message,
            List<ErrorDetail> details,
            String traceId
    ) {
        String resolvedMessage = message == null || message.isBlank()
                ? responseCode.defaultMessage()
                : message;
        return new Response<>(responseCode.name(), resolvedMessage, null, details, traceId);
    }
}
