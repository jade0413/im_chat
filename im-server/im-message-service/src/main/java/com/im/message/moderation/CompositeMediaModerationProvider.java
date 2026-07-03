package com.im.message.moderation;

import java.util.List;
import java.util.Optional;

public class CompositeMediaModerationProvider implements MediaModerationProvider {

  private final List<MediaModerationProvider> providers;

  public CompositeMediaModerationProvider(List<MediaModerationProvider> providers) {
    this.providers = providers == null ? List.of() : List.copyOf(providers);
  }

  @Override
  public Optional<MediaModerationMatch> scan(MediaModerationRequest request) {
    for (MediaModerationProvider provider : providers) {
      Optional<MediaModerationMatch> match = provider.scan(request);
      if (match.isPresent()) {
        return match;
      }
    }
    return Optional.empty();
  }
}
