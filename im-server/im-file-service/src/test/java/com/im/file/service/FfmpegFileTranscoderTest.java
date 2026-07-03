package com.im.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.im.file.config.FileProperties;
import com.im.file.dao.entity.FileTranscodeJobEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FfmpegFileTranscoderTest {

  @TempDir
  private Path tempDir;

  @Mock
  private ObjectStorageClient storageClient;

  @Test
  void transcodeDownloadsSourceRunsFfmpegAndUploadsTarget() throws Exception {
    FileProperties properties = properties(true);
    FfmpegFileTranscoder transcoder = new FfmpegFileTranscoder(
        properties,
        storageClient,
        (source, target, transcode) -> {
          assertThat(source).exists();
          Files.writeString(target, "mp4");
        });
    FileTranscodeJobEntity job = job(FileTranscodeConstants.PROFILE_MP4_720P);

    org.mockito.Mockito.doAnswer(invocation -> {
      Path destination = invocation.getArgument(2);
      Files.writeString(destination, "source");
      return null;
    }).when(storageClient).downloadObject("im-media", "1/202607/a video.mov", tempDir.resolve("7001.mov"));

    TranscodeResult result = transcoder.transcode(job);

    assertThat(result.skipped()).isFalse();
    assertThat(result.targetObjectKey())
        .isEqualTo("1/202607/transcoded/a_video_mp4_720p.mp4");
    verify(storageClient).uploadObject(
        "im-media",
        "1/202607/transcoded/a_video_mp4_720p.mp4",
        tempDir.resolve("7001_mp4_720p.mp4"),
        "video/mp4");
    assertThat(tempDir.resolve("7001.mov")).doesNotExist();
    assertThat(tempDir.resolve("7001_mp4_720p.mp4")).doesNotExist();
  }

  @Test
  void transcodeSkipsUnsupportedProfile() throws Exception {
    FfmpegFileTranscoder transcoder = new FfmpegFileTranscoder(
        properties(true),
        storageClient,
        (source, target, transcode) -> {
          throw new AssertionError("runner should not run");
        });

    TranscodeResult result = transcoder.transcode(job("hls"));

    assertThat(result.skipped()).isTrue();
    assertThat(result.reason()).contains("unsupported transcode profile");
  }

  private FileProperties properties(boolean transcodeEnabled) {
    return new FileProperties(
        "http://minio:9000",
        "http://localhost:9000",
        "ak",
        "sk",
        "im-media",
        Duration.ofMinutes(5),
        Duration.ofMinutes(15),
        "",
        Set.of("video/mp4"),
        new FileProperties.SizeLimit(1024L, 2048L, 4096L, 8192L),
        new FileProperties.Transcode(transcodeEnabled, "ffmpeg", tempDir.toString(), Duration.ofSeconds(5)));
  }

  private FileTranscodeJobEntity job(String profile) {
    FileTranscodeJobEntity job = new FileTranscodeJobEntity();
    job.setId(7001L);
    job.setTenantId(1L);
    job.setFileId(9001L);
    job.setSourceObjectKey("1/202607/a video.mov");
    job.setSourceMime("video/quicktime");
    job.setTargetProfile(profile);
    return job;
  }
}
