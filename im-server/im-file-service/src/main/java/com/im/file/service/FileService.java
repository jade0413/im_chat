package com.im.file.service;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.id.SnowflakeIdGenerator;
import com.im.common.tenant.TenantContext;
import com.im.file.config.FileProperties;
import com.im.file.dao.entity.FileMetaEntity;
import com.im.file.dao.mapper.FileMetaMapper;
import com.im.file.dto.ConfirmFileRequest;
import com.im.file.dto.FileMetaResponse;
import com.im.file.dto.PresignFileRequest;
import com.im.file.dto.PresignFileResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileService {

  private static final int OBJECT_KEY_LIMIT = 255;
  private static final int MIME_LIMIT = 64;

  private final FileMetaMapper fileMetaMapper;
  private final ObjectStorageClient storageClient;
  private final SnowflakeIdGenerator idGenerator;
  private final FileProperties properties;
  private final Clock clock;

  @Autowired
  public FileService(FileMetaMapper fileMetaMapper,
      ObjectStorageClient storageClient,
      SnowflakeIdGenerator idGenerator,
      FileProperties properties) {
    this(fileMetaMapper, storageClient, idGenerator, properties, Clock.systemUTC());
  }

  FileService(FileMetaMapper fileMetaMapper,
      ObjectStorageClient storageClient,
      SnowflakeIdGenerator idGenerator,
      FileProperties properties,
      Clock clock) {
    this.fileMetaMapper = fileMetaMapper;
    this.storageClient = storageClient;
    this.idGenerator = idGenerator;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public PresignFileResponse presign(long uploaderId, PresignFileRequest request) {
    long tenantId = TenantContext.requiredTenantId();
    validateUploader(uploaderId);
    String mime = validateMime(request == null ? null : request.mime());
    long size = request == null ? 0 : request.size();
    String fileName = request == null ? null : request.fileName();
    Integer durationMs = request == null ? null : request.durationMs();
    validateSize(mime, size);

    String objectKey = newObjectKey(tenantId, fileName, mime);
    FileMetaEntity entity = new FileMetaEntity();
    entity.setId(idGenerator.nextId());
    entity.setTenantId(tenantId);
    entity.setUploaderId(uploaderId);
    entity.setObjectKey(objectKey);
    entity.setMime(mime);
    entity.setSize(size);
    entity.setDurationMs(durationMs);
    entity.setStatus(FileMetaStatus.PENDING);
    entity.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    fileMetaMapper.insert(entity);

    String url = storageClient.presignPut(properties.bucket(), objectKey, properties.presignTtl());
    return new PresignFileResponse(
        entity.getId(),
        objectKey,
        url,
        clock.millis() + properties.presignTtl().toMillis(),
        Map.of("Content-Type", mime));
  }

  @Transactional
  public FileMetaResponse confirm(long uploaderId, ConfirmFileRequest request) {
    long tenantId = TenantContext.requiredTenantId();
    validateUploader(uploaderId);
    String objectKey = validateObjectKey(tenantId, request == null ? null : request.objectKey());

    FileMetaEntity entity = fileMetaMapper.selectByObjectKey(tenantId, objectKey);
    if (entity == null) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "file metadata not found");
    }
    if (!Long.valueOf(uploaderId).equals(entity.getUploaderId())) {
      throw new ImException(ErrorCode.NO_PERMISSION, "file uploader mismatch");
    }
    if (entity.getStatus() == FileMetaStatus.CONFIRMED) {
      return toResponse(entity);
    }
    if (entity.getStatus() != FileMetaStatus.PENDING) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "file status cannot be confirmed");
    }
    validateConfirmRequest(entity, request);
    ObjectStat stat = statObject(objectKey);
    validateStoredObject(entity, stat);

    int updated = fileMetaMapper.updateStatus(
        tenantId, objectKey, FileMetaStatus.PENDING, FileMetaStatus.CONFIRMED);
    if (updated != 1) {
      FileMetaEntity current = fileMetaMapper.selectByObjectKey(tenantId, objectKey);
      if (current != null && current.getStatus() == FileMetaStatus.CONFIRMED) {
        return toResponse(current);
      }
      throw new ImException(ErrorCode.INTERNAL_ERROR, "file confirm update failed");
    }
    entity.setStatus(FileMetaStatus.CONFIRMED);
    return toResponse(entity);
  }

  private void validateUploader(long uploaderId) {
    if (uploaderId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "valid uploader is required");
    }
  }

  private String validateMime(String mime) {
    String normalized = FileProperties.normalizeMime(mime);
    if (normalized.isBlank()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "mime is required");
    }
    if (normalized.length() > MIME_LIMIT) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "mime is too long");
    }
    if (!properties.isMimeAllowed(normalized)) {
      throw new ImException(ErrorCode.MIME_NOT_ALLOWED);
    }
    return normalized;
  }

  private void validateSize(String mime, long size) {
    if (size <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "file size is required");
    }
    if (size > properties.maxBytesFor(mime)) {
      throw new ImException(ErrorCode.FILE_TOO_LARGE);
    }
  }

  private String validateObjectKey(long tenantId, String objectKey) {
    if (objectKey == null || objectKey.isBlank()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "object_key is required");
    }
    String normalized = objectKey.trim();
    if (normalized.length() > OBJECT_KEY_LIMIT) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "object_key is too long");
    }
    String tenantPrefix = tenantId + "/";
    if (!normalized.startsWith(tenantPrefix) || normalized.contains("..")) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "object_key tenant prefix is invalid");
    }
    return normalized;
  }

  private String newObjectKey(long tenantId, String fileName, String mime) {
    String month = YearMonth.now(clock).toString().replace("-", "");
    return tenantId + "/" + month + "/" + UUID.randomUUID() + extension(fileName, mime);
  }

  private String extension(String fileName, String mime) {
    String sanitized = sanitizeExtension(fileName);
    if (!sanitized.isBlank()) {
      return sanitized;
    }
    return switch (mime) {
      case "image/jpeg" -> ".jpg";
      case "image/png" -> ".png";
      case "image/webp" -> ".webp";
      case "image/gif" -> ".gif";
      case "audio/aac" -> ".aac";
      case "audio/mpeg" -> ".mp3";
      case "audio/ogg" -> ".ogg";
      case "audio/opus" -> ".opus";
      case "audio/wav" -> ".wav";
      case "audio/mp4" -> ".m4a";
      case "application/pdf" -> ".pdf";
      case "application/zip" -> ".zip";
      case "text/plain" -> ".txt";
      default -> "";
    };
  }

  private String sanitizeExtension(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return "";
    }
    String normalized = fileName.trim().toLowerCase(Locale.ROOT);
    int dot = normalized.lastIndexOf('.');
    if (dot < 0 || dot == normalized.length() - 1) {
      return "";
    }
    String ext = normalized.substring(dot);
    if (ext.length() > 16 || !ext.matches("\\.[a-z0-9]+")) {
      return "";
    }
    return ext;
  }

  private void validateConfirmRequest(FileMetaEntity entity, ConfirmFileRequest request) {
    if (request == null) {
      return;
    }
    if (request.size() != null && !request.size().equals(entity.getSize())) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "confirmed size mismatch");
    }
    if (request.mime() != null && !FileProperties.normalizeMime(request.mime()).equals(entity.getMime())) {
      throw new ImException(ErrorCode.MIME_NOT_ALLOWED, "confirmed mime mismatch");
    }
  }

  private ObjectStat statObject(String objectKey) {
    try {
      return storageClient.statObject(properties.bucket(), objectKey);
    } catch (ObjectStorageException ex) {
      if (ex.notFound()) {
        throw new ImException(ErrorCode.VALIDATION_FAILED, "uploaded object not found");
      }
      throw new ImException(ErrorCode.INTERNAL_ERROR, "object storage unavailable", ex);
    }
  }

  private void validateStoredObject(FileMetaEntity entity, ObjectStat stat) {
    if (stat.size() != entity.getSize()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "uploaded object size mismatch");
    }
    String contentType = FileProperties.normalizeMime(stat.contentType());
    if (!contentType.isBlank() && !contentType.equals(entity.getMime())) {
      throw new ImException(ErrorCode.MIME_NOT_ALLOWED, "uploaded object mime mismatch");
    }
  }

  private FileMetaResponse toResponse(FileMetaEntity entity) {
    return new FileMetaResponse(
        entity.getId(),
        entity.getObjectKey(),
        entity.getMime(),
        entity.getSize(),
        entity.getDurationMs(),
        entity.getStatus());
  }
}
