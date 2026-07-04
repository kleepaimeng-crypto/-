package com.cabin.common.trace;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTests {
    @Test
    void usesSafeIncomingTraceIdAndClearsMdc() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TraceContext.TRACE_ID_HEADER, "trace-123");

        FilterChain chain = (servletRequest, servletResponse) ->
                assertThat(MDC.get(TraceContext.TRACE_ID)).isEqualTo("trace-123");

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(TraceContext.TRACE_ID_HEADER)).isEqualTo("trace-123");
        assertThat(request.getAttribute(TraceContext.TRACE_ID_ATTRIBUTE)).isEqualTo("trace-123");
        assertThat(MDC.get(TraceContext.TRACE_ID)).isNull();
    }

    @Test
    void replacesUnsafeIncomingTraceId() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TraceContext.TRACE_ID_HEADER, "bad trace id");

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        assertThat(response.getHeader(TraceContext.TRACE_ID_HEADER))
                .isNotBlank()
                .isNotEqualTo("bad trace id");
    }
}
