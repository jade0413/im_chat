package com.im.common.trace;

import java.util.Optional;
import java.util.concurrent.Callable;
import org.slf4j.MDC;

public final class TraceContext {

  public static final String TRACE_ID_KEY = "trace_id";

  private TraceContext() {
  }

  public static Optional<String> currentTraceId() {
    return Optional.ofNullable(MDC.get(TRACE_ID_KEY)).filter(value -> !value.isBlank());
  }

  public static String currentOrCreateTraceId() {
    return currentTraceId().orElseGet(TraceIdGenerator::generate);
  }

  public static void setTraceId(String traceId) {
    if (traceId == null || traceId.isBlank()) {
      MDC.remove(TRACE_ID_KEY);
      return;
    }
    MDC.put(TRACE_ID_KEY, traceId);
  }

  public static void clear() {
    MDC.remove(TRACE_ID_KEY);
  }

  public static void runWithTraceId(String traceId, Runnable action) {
    String previous = MDC.get(TRACE_ID_KEY);
    setTraceId(traceId);
    try {
      action.run();
    } finally {
      restore(previous);
    }
  }

  public static <T> T callWithTraceId(String traceId, Callable<T> action) throws Exception {
    String previous = MDC.get(TRACE_ID_KEY);
    setTraceId(traceId);
    try {
      return action.call();
    } finally {
      restore(previous);
    }
  }

  private static void restore(String previous) {
    if (previous == null || previous.isBlank()) {
      MDC.remove(TRACE_ID_KEY);
    } else {
      MDC.put(TRACE_ID_KEY, previous);
    }
  }
}
