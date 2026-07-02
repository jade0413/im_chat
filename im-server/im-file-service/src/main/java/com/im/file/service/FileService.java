package com.im.file.service;

import com.im.common.file.FileMetaConstants;
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
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FileService {

  private static final int OBJECT_KEY_LIMIT = 255;
  private static final int MIME_LIMIT = 64;

  private final FileMetaMapper fileMetaMapper;
  private final ObjectStorageClient storageClient;
  private final SnowflakeIdGenerator idGenerator;
  private final FileProperties properties;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  public FileService(FileMetaMapper fileMetaMapper,
      ObjectStorageClient storageClient,
      SnowflakeIdGenerator idGenerator,
      FileProperties properties,
      TransactionTemplate transactionTemplate,
      @Qualifier("fileClock") Clock clock) {
    this.fileMetaMapper = fileMetaMapper;
    this.storageClient = storageClient;
    this.idGenerator = idGenerator;
    this.properties = properties;
    this.transactionTemplate = transactionTemplate;
    this.clock = clock;
  }

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
    entity.setStatus(FileMetaConstants.STATUS_PENDING);
    entity.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    FileMetaEntity inserted = Objects.requireNonNull(transactionTemplate.execute(status -> {
      fileMetaMapper.insert(entity);
      return entity;
    }));

    String url = storageClient.presignPut(properties.bucket(), inserted.getObjectKey(), properties.presignTtl());
    return new PresignFileResponse(
        inserted.getId(),
        inserted.getObjectKey(),
        url,
        clock.millis() + properties.presignTtl().toMillis(),
        Map.of("Content-Type", inserted.getMime()));
  }

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
    if (isStatus(entity, FileMetaConstants.STATUS_CONFIRMED)) {
      return toResponse(entity);
    }
    if (!isStatus(entity, FileMetaConstants.STATUS_PENDING)) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "file status cannot be confirmed");
    }
    validateConfirmRequest(entity, request);
    ObjectStat stat = statObject(objectKey);
    return Objects.requireNonNull(transactionTemplate.execute(
        status -> confirmAfterStat(tenantId, uploaderId, objectKey, request, stat)));
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
      case "image/heic" -> ".heic";
      case "image/heif" -> ".heif";
      case "audio/aac" -> ".aac";
      case "audio/mpeg" -> ".mp3";
      case "audio/ogg" -> ".ogg";
      case "audio/opus" -> ".opus";
      case "audio/wav" -> ".wav";
      case "audio/mp4" -> ".m4a";
      case "application/pdf" -> ".pdf";
      case "application/zip" -> ".zip";
      case "application/msword" -> ".doc";
      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
      case "application/vnd.ms-excel" -> ".xls";
      case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> ".xlsx";
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
  }

  private FileMetaResponse confirmAfterStat(long tenantId, long uploaderId, String objectKey,
      ConfirmFileRequest request, ObjectStat stat) {
    FileMetaEntity current = fileMetaMapper.selectByObjectKey(tenantId, objectKey);
    if (current == null) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "file metadata not found");
    }
    if (!Long.valueOf(uploaderId).equals(current.getUploaderId())) {
      throw new ImException(ErrorCode.NO_PERMISSION, "file uploader mismatch");
    }
    if (isStatus(current, FileMetaConstants.STATUS_CONFIRMED)) {
      return toResponse(current);
    }
    if (!isStatus(current, FileMetaConstants.STATUS_PENDING)) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "file status cannot be confirmed");
    }
    validateConfirmRequest(current, request);
    validateStoredObject(current, stat);

    int updated = fileMetaMapper.updateStatus(
        tenantId, objectKey, FileMetaConstants.STATUS_PENDING, FileMetaConstants.STATUS_CONFIRMED);
    if (updated != 1) {
      FileMetaEntity latest = fileMetaMapper.selectByObjectKey(tenantId, objectKey);
      if (latest != null && isStatus(latest, FileMetaConstants.STATUS_CONFIRMED)) {
        return toResponse(latest);
      }
      throw new ImException(ErrorCode.INTERNAL_ERROR, "file confirm update failed");
    }
    current.setStatus(FileMetaConstants.STATUS_CONFIRMED);
    return toResponse(current);
  }

  /**
   * 生成文件下载预签名 URL（15 分钟有效），仅允许已确认的文件。
   */
  public String presignDownload(long requestUserId, String objectKey) {
    long tenantId = TenantContext.requiredTenantId();
    validateUploader(requestUserId);
    String normalized = validateObjectKey(tenantId, objectKey);
    FileMetaEntity entity = fileMetaMapper.selectByObjectKey(tenantId, normalized);
    if (entity == null || !isStatus(entity, FileMetaConstants.STATUS_CONFIRMED)) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "file not found or not confirmed");
    }
    return storageClient.presignGet(properties.bucket(), normalized, java.time.Duration.ofMinutes(15));
  }

  private boolean isStatus(FileMetaEntity entity, int status) {
    return entity.getStatus() != null && entity.getStatus() == status;
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
