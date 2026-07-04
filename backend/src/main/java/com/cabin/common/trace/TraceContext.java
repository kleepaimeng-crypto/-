package com.cabin.common.trace;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.MDC;

public final class TraceContext {
    public static final String TRACE_ID = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_ATTRIBUTE = TraceContext.class.getName() + ".TRACE_ID";

    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceContext() {
    }

    public static String currentTraceId() {
        return Optional.ofNullable(MDC.get(TRACE_ID))
                .filter(value -> !value.isBlank())
                .orElseGet(TraceContext::newTraceId);
    }

    public static String newTraceId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
