package com.im.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.im.common.id.SnowflakeIdGenerator;
import com.im.file.dao.entity.FileMetaEntity;
import com.im.file.dao.entity.FileTranscodeJobEntity;
import com.im.file.dao.mapper.FileTranscodeJobMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileTranscodeJobServiceTest {

  @Mock
  private FileTranscodeJobMapper jobMapper;

  @Mock
  private SnowflakeIdGenerator idGenerator;

  @Captor
  private ArgumentCaptor<FileTranscodeJobEntity> jobCaptor;

  private FileTranscodeJobService service;

  @BeforeEach
  void setUp() {
    service = new FileTranscodeJobService(
        jobMapper,
        idGenerator,
        Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void enqueueAfterConfirmCreatesPendingJobForVideo() {
    FileMetaEntity file = file("video/mp4");
    when(idGenerator.nextId()).thenReturn(7001L);
    when(jobMapper.insertIgnore(any(FileTranscodeJobEntity.class))).thenReturn(1);

    boolean inserted = service.enqueueAfterConfirm(file);

    assertThat(inserted).isTrue();
    verify(jobMapper).insertIgnore(jobCaptor.capture());
    FileTranscodeJobEntity job = jobCaptor.getValue();
    assertThat(job.getId()).isEqualTo(7001L);
    assertThat(job.getTenantId()).isEqualTo(1L);
    assertThat(job.getFileId()).isEqualTo(9001L);
    assertThat(job.getSourceObjectKey()).isEqualTo("1/202606/a.mp4");
    assertThat(job.getSourceMime()).isEqualTo("video/mp4");
    assertThat(job.getTargetProfile()).isEqualTo(FileTranscodeConstants.PROFILE_MP4_720P);
    assertThat(job.getStatus()).isEqualTo(FileTranscodeConstants.STATUS_PENDING);
    assertThat(job.getRetryCount()).isZero();
    assertThat(job.getCreatedAt()).isEqualTo(LocalDateTime.parse("2026-06-13T00:00:00"));
    assertThat(job.getUpdatedAt()).isEqualTo(LocalDateTime.parse("2026-06-13T00:00:00"));
  }

  @Test
  void enqueueAfterConfirmSkipsNonVideo() {
    boolean inserted = service.enqueueAfterConfirm(file("image/png"));

    assertThat(inserted).isFalse();
    verify(jobMapper, never()).insertIgnore(any(FileTranscodeJobEntity.class));
  }

  @Test
  void claimNextDueMovesDueJobToProcessing() {
    LocalDateTime now = LocalDateTime.parse("2026-06-13T00:00:00");
    FileTranscodeJobEntity job = job();
    when(jobMapper.selectNextDue(
        now,
        FileTranscodeConstants.STATUS_PENDING,
        FileTranscodeConstants.STATUS_FAILED,
        FileTranscodeConstants.STATUS_PROCESSING,
        FileTranscodeConstants.DEFAULT_MAX_ATTEMPTS))
        .thenReturn(job);
    when(jobMapper.claim(
        7001L,
        "worker-a",
        now.plusMinutes(5),
        now,
        FileTranscodeConstants.STATUS_PROCESSING,
        FileTranscodeConstants.STATUS_PENDING,
        FileTranscodeConstants.STATUS_FAILED,
        FileTranscodeConstants.DEFAULT_MAX_ATTEMPTS))
        .thenReturn(1);

    var claimed = service.claimNextDue("worker-a");

    assertThat(claimed).isPresent();
    assertThat(claimed.get().getStatus()).isEqualTo(FileTranscodeConstants.STATUS_PROCESSING);
    assertThat(claimed.get().getClaimOwner()).isEqualTo("worker-a");
    assertThat(claimed.get().getClaimUntil()).isEqualTo(now.plusMinutes(5));
  }

  @Test
  void markFailedSchedulesRetryAndClearsClaim() {
    LocalDateTime now = LocalDateTime.parse("2026-06-13T00:00:00");
    FileTranscodeJobEntity job = job();
    when(jobMapper.markFailed(
        eq(7001L),
        eq("worker-a"),
        eq("ffmpeg failed"),
        eq(now.plusMinutes(5)),
        eq(now),
        eq(FileTranscodeConstants.STATUS_FAILED),
        eq(FileTranscodeConstants.STATUS_PROCESSING)))
        .thenReturn(1);

    boolean updated = service.markFailed(job, "worker-a", new RuntimeException("ffmpeg failed"));

    assertThat(updated).isTrue();
  }

  @Test
  void markSucceededStoresTargetObjectKey() {
    LocalDateTime now = LocalDateTime.parse("2026-06-13T00:00:00");
    FileTranscodeJobEntity job = job();
    when(jobMapper.markSucceeded(
        7001L,
        "worker-a",
        "1/202606/a_720p.mp4",
        now,
        FileTranscodeConstants.STATUS_SUCCEEDED,
        FileTranscodeConstants.STATUS_PROCESSING))
        .thenReturn(1);

    boolean updated = service.markSucceeded(job, "worker-a", "1/202606/a_720p.mp4");

    assertThat(updated).isTrue();
  }

  @Test
  void succeededTargetObjectKeyReturnsCompletedTarget() {
    when(jobMapper.selectSucceededTargetObjectKey(
        1L,
        9001L,
        FileTranscodeConstants.PROFILE_MP4_720P,
        FileTranscodeConstants.STATUS_SUCCEEDED))
        .thenReturn("1/202606/transcoded/a_mp4_720p.mp4");

    var target = service.succeededTargetObjectKey(
        1L,
        9001L,
        FileTranscodeConstants.PROFILE_MP4_720P);

    assertThat(target).contains("1/202606/transcoded/a_mp4_720p.mp4");
  }

  private FileMetaEntity file(String mime) {
    FileMetaEntity file = new FileMetaEntity();
    file.setId(9001L);
    file.setTenantId(1L);
    file.setObjectKey("1/202606/a.mp4");
    file.setMime(mime);
    return file;
  }

  private FileTranscodeJobEntity job() {
    FileTranscodeJobEntity job = new FileTranscodeJobEntity();
    job.setId(7001L);
    job.setTenantId(1L);
    job.setFileId(9001L);
    job.setSourceObjectKey("1/202606/a.mp4");
    job.setSourceMime("video/mp4");
    job.setTargetProfile(FileTranscodeConstants.PROFILE_MP4_720P);
    job.setStatus(FileTranscodeConstants.STATUS_PENDING);
    job.setRetryCount(0);
    return job;
  }
}
