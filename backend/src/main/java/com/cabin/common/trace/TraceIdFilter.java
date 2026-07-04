package com.cabin.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TraceIdFilter extends OncePerRequestFilter {
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        request.setAttribute(TraceContext.TRACE_ID_ATTRIBUTE, traceId);
        response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
        MDC.put(TraceContext.TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceContext.TRACE_ID);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String headerValue = request.getHeader(TraceContext.TRACE_ID_HEADER);
        if (headerValue != null && SAFE_TRACE_ID.matcher(headerValue).matches()) {
            return headerValue;
        }
        return TraceContext.newTraceId();
    }
}
