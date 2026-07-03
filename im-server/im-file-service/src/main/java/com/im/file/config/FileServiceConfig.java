package com.im.file.config;

import com.im.file.service.FfmpegFileTranscoder;
import com.im.file.service.FileTranscoder;
import com.im.file.service.NoopFileTranscoder;
import com.im.file.service.ObjectStorageClient;
import com.im.file.service.ProcessFfmpegRunner;
import io.minio.MinioClient;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FileProperties.class)
public class FileServiceConfig {

  @Bean
  @Qualifier("minioInternalClient")
  public MinioClient minioInternalClient(FileProperties properties) {
    return MinioClient.builder()
        .endpoint(properties.endpoint())
        .credentials(properties.accessKey(), properties.secretKey())
        .build();
  }

  @Bean
  @Qualifier("minioPresignClient")
  public MinioClient minioPresignClient(FileProperties properties) {
    return MinioClient.builder()
        .endpoint(properties.publicEndpoint())
        .credentials(properties.accessKey(), properties.secretKey())
        .build();
  }

  @Bean
  public Clock fileClock() {
    return Clock.systemUTC();
  }

  @Bean
  public FileTranscoder fileTranscoder(FileProperties properties,
      ObjectStorageClient storageClient) {
    if (properties.transcode().enabled()) {
      return new FfmpegFileTranscoder(properties, storageClient, new ProcessFfmpegRunner());
    }
    return new NoopFileTranscoder();
  }
}
