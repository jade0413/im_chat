package com.im.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.im.file.dao.entity.FileTranscodeJobEntity;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileTranscodeProcessorTest {

  @Mock
  private FileTranscodeJobService jobService;

  @Mock
  private FileTranscoder transcoder;

  private FileTranscodeProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new FileTranscodeProcessor(jobService, transcoder);
  }

  @Test
  void processOneReturnsFalseWhenNoDueJob() throws Exception {
    when(jobService.claimNextDue("worker-a")).thenReturn(Optional.empty());

    boolean processed = processor.processOne("worker-a");

    assertThat(processed).isFalse();
    verify(transcoder, never()).transcode(any());
  }

  @Test
  void processOneMarksSuccessWhenTranscoderReturnsTarget() throws Exception {
    FileTranscodeJobEntity job = job();
    when(jobService.claimNextDue("worker-a")).thenReturn(Optional.of(job));
    when(transcoder.transcode(job)).thenReturn(TranscodeResult.succeeded("1/202606/a_720p.mp4"));

    boolean processed = processor.processOne("worker-a");

    assertThat(processed).isTrue();
    verify(jobService).markSucceeded(job, "worker-a", "1/202606/a_720p.mp4");
  }

  @Test
  void processOneMarksSkippedWhenProviderIsNotConfigured() throws Exception {
    FileTranscodeJobEntity job = job();
    when(jobService.claimNextDue("worker-a")).thenReturn(Optional.of(job));
    when(transcoder.transcode(job)).thenReturn(TranscodeResult.skipped("transcoder provider not configured"));

    boolean processed = processor.processOne("worker-a");

    assertThat(processed).isTrue();
    verify(jobService).markSkipped(job, "worker-a", "transcoder provider not configured");
  }

  @Test
  void processOneMarksFailedWhenTranscoderThrows() throws Exception {
    FileTranscodeJobEntity job = job();
    RuntimeException error = new RuntimeException("ffmpeg failed");
    when(jobService.claimNextDue("worker-a")).thenReturn(Optional.of(job));
    when(transcoder.transcode(job)).thenThrow(error);

    boolean processed = processor.processOne("worker-a");

    assertThat(processed).isTrue();
    verify(jobService).markFailed(job, "worker-a", error);
  }

  private FileTranscodeJobEntity job() {
    FileTranscodeJobEntity job = new FileTranscodeJobEntity();
    job.setId(7001L);
    job.setSourceObjectKey("1/202606/a.mp4");
    job.setTargetProfile(FileTranscodeConstants.PROFILE_MP4_720P);
    return job;
  }
}
