package com.im.file.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FileProperties.class)
public class FileServiceConfig {

  @Bean
  public MinioClient minioClient(FileProperties properties) {
    return MinioClient.builder()
        .endpoint(properties.endpoint())
        .credentials(properties.accessKey(), properties.secretKey())
        .build();
  }
}
