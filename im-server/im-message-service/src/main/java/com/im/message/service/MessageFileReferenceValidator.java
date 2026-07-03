package com.im.message.service;

import com.im.common.file.FileMetaConstants;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.message.dao.entity.MessageFileMetaEntity;
import com.im.message.dao.mapper.MessageFileMetaMapper;
import com.im.proto.common.FileContent;
import com.im.proto.common.ImageContent;
import com.im.proto.common.MsgContent;
import com.im.proto.common.VoiceContent;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class MessageFileReferenceValidator {

  private final MessageFileMetaMapper fileMetaMapper;

  public MessageFileReferenceValidator(MessageFileMetaMapper fileMetaMapper) {
    this.fileMetaMapper = fileMetaMapper;
  }

  public void ensureReferencesConfirmed(long tenantId, MsgContent content) {
    switch (content.getContentCase()) {
      case IMAGE -> validateImage(tenantId, content.getImage());
      case VOICE -> validateVoice(tenantId, content.getVoice());
      case FILE -> validateFile(tenantId, content.getFile());
      default -> {
      }
    }
  }

  private void validateImage(long tenantId, ImageContent image) {
    validatePositive(image.getSize(), "image size is required");
    String mime = normalizeMime(image.getMime());
    if (mime.isBlank() || !mime.startsWith("image/")) {
      throw new ImException(ErrorCode.MIME_NOT_ALLOWED, "image mime is invalid");
    }
    MessageFileMetaEntity meta = confirmedFile(tenantId, image.getObjectKey());
    validateSize(meta, image.getSize());
    validateMime(meta, mime);
    validateThumbnailIfPresent(tenantId, image.getThumbKey());
  }

  private void validateVoice(long tenantId, VoiceContent voice) {
    validatePositive(voice.getSize(), "voice size is required");
    if (voice.getDurationMs() <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "voice duration is required");
    }
    MessageFileMetaEntity meta = confirmedFile(tenantId, voice.getObjectKey());
    if (!normalizeMime(meta.getMime()).startsWith("audio/")) {
      throw new ImException(ErrorCode.MIME_NOT_ALLOWED, "voice file mime is invalid");
    }
    validateSize(meta, voice.getSize());
  }

  private void validateFile(long tenantId, FileContent file) {
    validatePositive(file.getSize(), "file size is required");
    if (file.getFileName() == null || file.getFileName().isBlank()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "file_name is required");
    }
    String mime = normalizeMime(file.getMime());
    if (mime.isBlank()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "file mime is required");
    }
    MessageFileMetaEntity meta = confirmedFile(tenantId, file.getObjectKey());
    validateSize(meta, file.getSize());
    validateMime(meta, mime);
    validateThumbnailIfPresent(tenantId, file.getThumbKey());
  }

  private MessageFileMetaEntity confirmedFile(long tenantId, String objectKey) {
    String normalized = validateObjectKey(tenantId, objectKey);
    MessageFileMetaEntity meta = fileMetaMapper.selectByObjectKey(tenantId, normalized);
    if (meta == null || meta.getStatus() == null
        || meta.getStatus() != FileMetaConstants.STATUS_CONFIRMED) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "file is not confirmed");
    }
    return meta;
  }

  private String validateObjectKey(long tenantId, String objectKey) {
    if (objectKey == null || objectKey.isBlank()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "object_key is required");
    }
    String normalized = objectKey.trim();
    String tenantPrefix = tenantId + "/";
    if (!normalized.startsWith(tenantPrefix) || normalized.contains("..")) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "object_key tenant prefix is invalid");
    }
    return normalized;
  }

  private void validatePositive(long size, String message) {
    if (size <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, message);
    }
  }

  private void validateSize(MessageFileMetaEntity meta, long size) {
    if (meta.getSize() == null || meta.getSize() != size) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "file size mismatch");
    }
  }

  private void validateMime(MessageFileMetaEntity meta, String expectedMime) {
    if (!normalizeMime(meta.getMime()).equals(expectedMime)) {
      throw new ImException(ErrorCode.MIME_NOT_ALLOWED, "file mime mismatch");
    }
  }

  private void validateThumbnailIfPresent(long tenantId, String thumbKey) {
    if (thumbKey == null || thumbKey.isBlank()) {
      return;
    }
    MessageFileMetaEntity thumb = confirmedFile(tenantId, thumbKey);
    if (!normalizeMime(thumb.getMime()).startsWith("image/")) {
      throw new ImException(ErrorCode.MIME_NOT_ALLOWED, "thumbnail mime is invalid");
    }
  }

  private String normalizeMime(String mime) {
    return mime == null ? "" : mime.trim().toLowerCase(Locale.ROOT);
  }
}
