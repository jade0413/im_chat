package com.im.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.im.file.config.FileProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileTranscodeWorkerTest {

  @Mock
  private FileTranscodeProcessor processor;

  @Test
  void startDoesNothingWhenTranscodeDisabled() {
    FileTranscodeWorker worker = worker(false, 2);

    worker.start();

    assertThat(worker.isRunning()).isFalse();
    verifyNoInteractions(processor);
  }

  @Test
  void pollOnceStopsAtBatchSize() {
    FileTranscodeWorker worker = worker(true, 2);
    when(processor.processOne("worker-a")).thenReturn(true, true, true);

    int processed = worker.pollOnce();

    assertThat(processed).isEqualTo(2);
    verify(processor, times(2)).processOne("worker-a");
  }

  @Test
  void pollOnceStopsWhenQueueIsEmpty() {
    FileTranscodeWorker worker = worker(true, 4);
    when(processor.processOne("worker-a")).thenReturn(true, false);

    int processed = worker.pollOnce();

    assertThat(processed).isEqualTo(1);
    verify(processor, times(2)).processOne("worker-a");
  }

  @Test
  void pollOnceBreaksOnUnexpectedProcessorError() {
    FileTranscodeWorker worker = worker(true, 4);
    when(processor.processOne("worker-a")).thenThrow(new RuntimeException("boom"));

    int processed = worker.pollOnce();

    assertThat(processed).isZero();
    verify(processor).processOne("worker-a");
  }

  private FileTranscodeWorker worker(boolean enabled, int batchSize) {
    return new FileTranscodeWorker(
        processor,
        new FileProperties.Transcode(
            enabled,
            "ffmpeg",
            "/tmp/im-file-transcode",
            Duration.ofSeconds(5),
            Duration.ofMillis(50),
            batchSize),
        "worker-a");
  }
}
