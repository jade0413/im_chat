package com.im.message.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CompositeMediaModerationProviderTest {

  @Test
  void returnsFirstProviderMatchAndStopsScanning() {
    AtomicInteger secondCalls = new AtomicInteger();
    MediaModerationProvider first = request -> Optional.of(new MediaModerationMatch(
        "first", "image", BigDecimal.ONE.setScale(4), "hit"));
    MediaModerationProvider second = request -> {
      secondCalls.incrementAndGet();
      return Optional.empty();
    };

    Optional<MediaModerationMatch> result = new CompositeMediaModerationProvider(
        List.of(first, second)).scan(request());

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().provider()).isEqualTo("first");
    assertThat(secondCalls).hasValue(0);
  }

  @Test
  void continuesUntilProviderMatches() {
    MediaModerationProvider clean = request -> Optional.empty();
    MediaModerationProvider hit = request -> Optional.of(new MediaModerationMatch(
        "second", "video", BigDecimal.ONE.setScale(4), "hit"));

    Optional<MediaModerationMatch> result = new CompositeMediaModerationProvider(
        List.of(clean, hit)).scan(request());

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().provider()).isEqualTo("second");
  }

  private MediaModerationRequest request() {
    return new MediaModerationRequest(1L, 9003L, "1/202607/a.png", "image/png", 100L, "image");
  }
}
