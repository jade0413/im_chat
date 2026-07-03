package com.im.file.service;

public record TranscodeResult(boolean skipped, String targetObjectKey, String reason) {

  public static TranscodeResult succeeded(String targetObjectKey) {
    return new TranscodeResult(false, targetObjectKey, null);
  }

  public static TranscodeResult skipped(String reason) {
    return new TranscodeResult(true, null, reason);
  }
}
