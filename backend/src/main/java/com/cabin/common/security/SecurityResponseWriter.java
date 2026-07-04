package com.cabin.common.security;

import com.cabin.common.response.Response;
import com.cabin.common.response.ResponseCode;
import com.cabin.common.trace.TraceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class SecurityResponseWriter {
    private SecurityResponseWriter() {
    }

    static void write(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            ResponseCode code,
            String message
    ) throws IOException {
        response.setStatus(code.httpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(
                response.getWriter(),
                Response.error(code, message, TraceContext.currentTraceId())
        );
    }
}
