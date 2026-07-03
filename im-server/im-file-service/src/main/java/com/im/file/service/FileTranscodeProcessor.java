package com.im.file.service;

import com.im.file.dao.entity.FileTranscodeJobEntity;
import org.springframework.stereotype.Service;

@Service
public class FileTranscodeProcessor {

  private final FileTranscodeJobService jobService;
  private final FileTranscoder transcoder;

  public FileTranscodeProcessor(FileTranscodeJobService jobService,
      FileTranscoder transcoder) {
    this.jobService = jobService;
    this.transcoder = transcoder;
  }

  public boolean processOne(String claimOwner) {
    return jobService.claimNextDue(claimOwner)
        .map(job -> processClaimed(job, claimOwner))
        .orElse(false);
  }

  private boolean processClaimed(FileTranscodeJobEntity job, String claimOwner) {
    try {
      TranscodeResult result = transcoder.transcode(job);
      if (result == null) {
        jobService.markFailed(job, claimOwner, new IllegalStateException("transcoder returned null"));
      } else if (result.skipped()) {
        jobService.markSkipped(job, claimOwner, result.reason());
      } else if (result.targetObjectKey() == null || result.targetObjectKey().isBlank()) {
        jobService.markFailed(job, claimOwner, new IllegalStateException("transcoder returned empty target"));
      } else {
        jobService.markSucceeded(job, claimOwner, result.targetObjectKey());
      }
    } catch (Exception ex) {
      jobService.markFailed(job, claimOwner, ex);
    }
    return true;
  }
}
