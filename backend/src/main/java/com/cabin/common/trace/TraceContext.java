package com.cabin.common.trace;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.slf4j.MDC;

public final class TraceContext {
    public static final String TRACE_ID = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_ATTRIBUTE = "traceId";

    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceContext() {
    }

    public static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID);
        return traceId == null || traceId.isBlank() ? newTraceId() : traceId;
    }

    public static String newTraceId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static boolean isSafeTraceId(String traceId) {
        return traceId != null && traceId.matches("[A-Za-z0-9._-]{8,64}");
    }
}
