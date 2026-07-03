package com.im.message.moderation;

import java.util.Optional;

public interface MediaModerationProvider {

  Optional<MediaModerationMatch> scan(MediaModerationRequest request);
}
