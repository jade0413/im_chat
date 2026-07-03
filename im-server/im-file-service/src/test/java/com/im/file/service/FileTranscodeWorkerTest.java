package com.im.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.im.file.config.FileProperties;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileTranscodeWorkerTest {

  @Mock
  private FileTranscodeProcessor processor;

  @Test
  void springCanInstantiateProductionConstructor() {
    new ApplicationContextRunner()
        .withUserConfiguration(WorkerBindingConfig.class)
        .withBean(FileTranscodeProcessor.class, () -> processor)
        .withBean(FileProperties.class, () -> new FileProperties(
            "http://minio:9000",
            "http://localhost:9000",
            "ak",
            "sk",
            "im-media",
            Duration.ofMinutes(5),
            Set.of("video/mp4"),
            null))
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(FileTranscodeWorker.class);
          assertThat(context.getBean(FileTranscodeWorker.class).isRunning()).isFalse();
        });
  }

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

  @Configuration(proxyBeanMethods = false)
  @Import(FileTranscodeWorker.class)
  static class WorkerBindingConfig {
  }
}
