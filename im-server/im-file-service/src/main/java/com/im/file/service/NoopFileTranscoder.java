package com.im.file.service;

import com.im.file.dao.entity.FileTranscodeJobEntity;

public class NoopFileTranscoder implements FileTranscoder {

  @Override
  public TranscodeResult transcode(FileTranscodeJobEntity job) {
    return TranscodeResult.skipped("transcoder provider not configured");
  }
}
