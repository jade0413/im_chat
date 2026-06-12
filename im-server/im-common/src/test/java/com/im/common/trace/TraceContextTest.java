package com.im.common.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TraceContextTest {

  @AfterEach
  void tearDown() {
    TraceContext.clear();
  }

  @Test
  void generatesTraceIdWithStablePrefix() {
    String traceId = TraceIdGenerator.generate();

    assertThat(traceId).startsWith("trc_");
    assertThat(traceId).hasSizeGreaterThan(12);
  }

  @Test
  void storesAndClearsCurrentTraceId() {
    TraceContext.setTraceId("trace-1");

    assertThat(TraceContext.currentTraceId()).contains("trace-1");

    TraceContext.clear();

    assertThat(TraceContext.currentTraceId()).isEmpty();
  }

  @Test
  void restoresPreviousTraceIdAfterScopedRunnable() {
    TraceContext.setTraceId("outer");

    TraceContext.runWithTraceId("inner", () ->
        assertThat(TraceContext.currentTraceId()).contains("inner"));

    assertThat(TraceContext.currentTraceId()).contains("outer");
  }

  @Test
  void restoresPreviousTraceIdAfterScopedCallable() throws Exception {
    TraceContext.setTraceId("outer");

    String value = TraceContext.callWithTraceId("inner", () ->
        TraceContext.currentTraceId().orElseThrow());

    assertThat(value).isEqualTo("inner");
    assertThat(TraceContext.currentTraceId()).contains("outer");
  }
}
