package com.cabin.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class TraceIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incomingTraceId = request.getHeader(TraceContext.TRACE_ID_HEADER);
        String traceId = TraceContext.isSafeTraceId(incomingTraceId)
                ? incomingTraceId
                : TraceContext.newTraceId();

        MDC.put(TraceContext.TRACE_ID, traceId);
        request.setAttribute(TraceContext.TRACE_ID_ATTRIBUTE, traceId);
        response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceContext.TRACE_ID);
        }
    }
}
