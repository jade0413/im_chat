package com.im.message.moderation;

import com.im.common.file.FileMetaConstants;
import com.im.message.dao.entity.MessageFileMetaEntity;
import com.im.message.dao.mapper.MessageFileMetaMapper;
import java.math.BigDecimal;
import java.util.Optional;

public class FileStatusMediaModerationProvider implements MediaModerationProvider {

  private static final BigDecimal EXACT_MATCH_SCORE = BigDecimal.ONE.setScale(4);

  private final MessageFileMetaMapper fileMetaMapper;

  public FileStatusMediaModerationProvider(MessageFileMetaMapper fileMetaMapper) {
    this.fileMetaMapper = fileMetaMapper;
  }

  @Override
  public Optional<MediaModerationMatch> scan(MediaModerationRequest request) {
    if (request == null || request.objectKey() == null || request.objectKey().isBlank()) {
      return Optional.empty();
    }
    MessageFileMetaEntity meta = fileMetaMapper.selectByObjectKey(
        request.tenantId(), request.objectKey().trim());
    if (meta == null || meta.getStatus() == null
        || meta.getStatus() != FileMetaConstants.STATUS_REJECTED) {
      return Optional.empty();
    }
    return Optional.of(new MediaModerationMatch(
        ModerationConstants.PROVIDER_FILE_STATUS,
        request.mediaType(),
        EXACT_MATCH_SCORE,
        request.mediaType() + ":" + request.objectKey()));
  }
}
