package com.im.file.service;

import com.im.file.config.FileProperties;
import com.im.file.dao.entity.FileTranscodeJobEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class FfmpegFileTranscoder implements FileTranscoder {

  private static final String TARGET_MIME = "video/mp4";

  private final FileProperties properties;
  private final ObjectStorageClient storageClient;
  private final FfmpegRunner runner;

  public FfmpegFileTranscoder(FileProperties properties,
      ObjectStorageClient storageClient,
      FfmpegRunner runner) {
    this.properties = properties;
    this.storageClient = storageClient;
    this.runner = runner;
  }

  @Override
  public TranscodeResult transcode(FileTranscodeJobEntity job) throws Exception {
    if (!FileTranscodeConstants.PROFILE_MP4_720P.equals(job.getTargetProfile())) {
      return TranscodeResult.skipped("unsupported transcode profile: " + job.getTargetProfile());
    }
    Path workDir = Path.of(properties.transcode().workDir());
    Files.createDirectories(workDir);
    Path source = workDir.resolve(job.getId() + sourceExtension(job.getSourceObjectKey()));
    Path target = workDir.resolve(job.getId() + "_" + job.getTargetProfile() + ".mp4");
    String targetObjectKey = targetObjectKey(job);
    try {
      storageClient.downloadObject(properties.bucket(), job.getSourceObjectKey(), source);
      runner.run(source, target, properties.transcode());
      if (!Files.exists(target) || Files.size(target) <= 0) {
        throw new IllegalStateException("ffmpeg did not produce output");
      }
      storageClient.uploadObject(properties.bucket(), targetObjectKey, target, TARGET_MIME);
      return TranscodeResult.succeeded(targetObjectKey);
    } finally {
      Files.deleteIfExists(source);
      Files.deleteIfExists(target);
    }
  }

  private String targetObjectKey(FileTranscodeJobEntity job) {
    String source = job.getSourceObjectKey();
    int slash = source.lastIndexOf('/');
    String dir = slash >= 0 ? source.substring(0, slash) : "";
    String name = slash >= 0 ? source.substring(slash + 1) : source;
    int dot = name.lastIndexOf('.');
    String base = dot > 0 ? name.substring(0, dot) : name;
    base = base.replaceAll("[^a-zA-Z0-9._-]+", "_");
    String prefix = dir.isBlank() ? "" : dir + "/";
    return prefix + "transcoded/" + base + "_" + job.getTargetProfile() + ".mp4";
  }

  private String sourceExtension(String objectKey) {
    String lower = objectKey == null ? "" : objectKey.toLowerCase(Locale.ROOT);
    int slash = lower.lastIndexOf('/');
    int dot = lower.lastIndexOf('.');
    if (dot <= slash || dot < 0 || lower.length() - dot > 12) {
      return ".video";
    }
    return lower.substring(dot);
  }
}
