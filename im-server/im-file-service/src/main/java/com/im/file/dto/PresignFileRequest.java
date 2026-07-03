package com.im.file.dto;

public record PresignFileRequest(
    String fileName,
    String mime,
    long size,
    Integer durationMs,
    String sha256
) {
  public PresignFileRequest(String fileName, String mime, long size, Integer durationMs) {
    this(fileName, mime, size, durationMs, null);
  }
}
