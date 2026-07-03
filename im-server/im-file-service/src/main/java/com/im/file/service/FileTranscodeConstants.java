package com.im.file.service;

public final class FileTranscodeConstants {

  private FileTranscodeConstants() {
  }

  public static final String PROFILE_MP4_720P = "mp4_720p";

  public static final int STATUS_PENDING = 0;
  public static final int STATUS_PROCESSING = 1;
  public static final int STATUS_SUCCEEDED = 2;
  public static final int STATUS_FAILED = 3;
  public static final int STATUS_SKIPPED = 4;

  public static final int DEFAULT_MAX_ATTEMPTS = 3;
  public static final int ERROR_MSG_LIMIT = 500;
}
