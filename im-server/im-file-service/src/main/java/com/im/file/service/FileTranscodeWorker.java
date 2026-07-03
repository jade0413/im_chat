package com.im.file.service;

import com.im.file.config.FileProperties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

@Service
public class FileTranscodeWorker implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(FileTranscodeWorker.class);

  private final FileTranscodeProcessor processor;
  private final FileProperties.Transcode properties;
  private final String claimOwner;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private ExecutorService executor;

  public FileTranscodeWorker(FileTranscodeProcessor processor,
      FileProperties properties) {
    this(processor, properties.transcode(), defaultClaimOwner());
  }

  FileTranscodeWorker(FileTranscodeProcessor processor,
      FileProperties.Transcode properties,
      String claimOwner) {
    this.processor = processor;
    this.properties = properties;
    this.claimOwner = claimOwner;
  }

  @Override
  public void start() {
    if (!properties.enabled() || !running.compareAndSet(false, true)) {
      return;
    }
    executor = Executors.newVirtualThreadPerTaskExecutor();
    executor.submit(this::loop);
    log.info("file transcode worker started, batch_size={}, interval_ms={}",
        properties.batchSize(), properties.pollInterval().toMillis());
  }

  @Override
  public void stop() {
    if (!running.compareAndSet(true, false)) {
      return;
    }
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  @Override
  public boolean isAutoStartup() {
    return true;
  }

  public int pollOnce() {
    int processed = 0;
    for (int i = 0; i < properties.batchSize(); i++) {
      try {
        if (!processor.processOne(claimOwner)) {
          break;
        }
        processed++;
      } catch (Exception ex) {
        log.warn("file transcode process failed", ex);
        break;
      }
    }
    return processed;
  }

  private void loop() {
    while (running.get()) {
      int processed = pollOnce();
      if (processed >= properties.batchSize()) {
        continue;
      }
      sleepInterval();
    }
  }

  private void sleepInterval() {
    try {
      TimeUnit.MILLISECONDS.sleep(properties.pollInterval().toMillis());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      running.set(false);
    }
  }

  private static String defaultClaimOwner() {
    return ProcessHandle.current().pid() + "-" + UUID.randomUUID();
  }
}
