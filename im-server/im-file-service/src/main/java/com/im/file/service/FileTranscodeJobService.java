package com.im.file.service;

import com.im.common.id.SnowflakeIdGenerator;
import com.im.file.dao.entity.FileMetaEntity;
import com.im.file.dao.entity.FileTranscodeJobEntity;
import com.im.file.dao.mapper.FileTranscodeJobMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class FileTranscodeJobService {

  private final FileTranscodeJobMapper jobMapper;
  private final SnowflakeIdGenerator idGenerator;
  private final Clock clock;
  private final int maxAttempts;
  private final Duration claimTtl;
  private final Duration retryDelay;

  public FileTranscodeJobService(FileTranscodeJobMapper jobMapper,
      SnowflakeIdGenerator idGenerator,
      @Qualifier("fileClock") Clock clock) {
    this.jobMapper = jobMapper;
    this.idGenerator = idGenerator;
    this.clock = clock;
    this.maxAttempts = FileTranscodeConstants.DEFAULT_MAX_ATTEMPTS;
    this.claimTtl = Duration.ofMinutes(5);
    this.retryDelay = Duration.ofMinutes(5);
  }

  public boolean enqueueAfterConfirm(FileMetaEntity file) {
    if (!requiresTranscode(file)) {
      return false;
    }
    LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    FileTranscodeJobEntity job = new FileTranscodeJobEntity();
    job.setId(idGenerator.nextId());
    job.setTenantId(file.getTenantId());
    job.setFileId(file.getId());
    job.setSourceObjectKey(file.getObjectKey());
    job.setSourceMime(file.getMime());
    job.setTargetProfile(FileTranscodeConstants.PROFILE_MP4_720P);
    job.setStatus(FileTranscodeConstants.STATUS_PENDING);
    job.setRetryCount(0);
    job.setCreatedAt(now);
    job.setUpdatedAt(now);
    return jobMapper.insertIgnore(job) == 1;
  }

  public boolean requiresTranscode(FileMetaEntity file) {
    String mime = file == null || file.getMime() == null ? "" : file.getMime().trim().toLowerCase();
    return mime.startsWith("video/");
  }

  public Optional<FileTranscodeJobEntity> claimNextDue(String claimOwner) {
    LocalDateTime now = now();
    FileTranscodeJobEntity next = jobMapper.selectNextDue(
        now,
        FileTranscodeConstants.STATUS_PENDING,
        FileTranscodeConstants.STATUS_FAILED,
        FileTranscodeConstants.STATUS_PROCESSING,
        maxAttempts);
    if (next == null) {
      return Optional.empty();
    }
    int claimed = jobMapper.claim(
        next.getId(),
        claimOwner,
        now.plus(claimTtl),
        now,
        FileTranscodeConstants.STATUS_PROCESSING,
        FileTranscodeConstants.STATUS_PENDING,
        FileTranscodeConstants.STATUS_FAILED,
        maxAttempts);
    if (claimed != 1) {
      return Optional.empty();
    }
    next.setStatus(FileTranscodeConstants.STATUS_PROCESSING);
    next.setClaimOwner(claimOwner);
    next.setClaimUntil(now.plus(claimTtl));
    next.setUpdatedAt(now);
    return Optional.of(next);
  }

  public boolean markSucceeded(FileTranscodeJobEntity job, String claimOwner, String targetObjectKey) {
    return jobMapper.markSucceeded(
        job.getId(),
        claimOwner,
        targetObjectKey,
        now(),
        FileTranscodeConstants.STATUS_SUCCEEDED,
        FileTranscodeConstants.STATUS_PROCESSING) == 1;
  }

  public boolean markSkipped(FileTranscodeJobEntity job, String claimOwner, String reason) {
    return jobMapper.markSkipped(
        job.getId(),
        claimOwner,
        truncate(reason),
        now(),
        FileTranscodeConstants.STATUS_SKIPPED,
        FileTranscodeConstants.STATUS_PROCESSING) == 1;
  }

  public boolean markFailed(FileTranscodeJobEntity job, String claimOwner, Exception error) {
    LocalDateTime now = now();
    return jobMapper.markFailed(
        job.getId(),
        claimOwner,
        truncate(error == null ? "unknown transcode error" : error.getMessage()),
        now.plus(retryDelay),
        now,
        FileTranscodeConstants.STATUS_FAILED,
        FileTranscodeConstants.STATUS_PROCESSING) == 1;
  }

  public Optional<String> succeededTargetObjectKey(long tenantId, long fileId, String targetProfile) {
    if (tenantId <= 0 || fileId <= 0 || targetProfile == null || targetProfile.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(jobMapper.selectSucceededTargetObjectKey(
        tenantId,
        fileId,
        targetProfile,
        FileTranscodeConstants.STATUS_SUCCEEDED));
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private String truncate(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String trimmed = value.trim();
    int limit = FileTranscodeConstants.ERROR_MSG_LIMIT;
    return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit);
  }
}
