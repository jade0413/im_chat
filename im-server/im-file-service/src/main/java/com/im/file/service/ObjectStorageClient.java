package com.im.file.service;

import java.nio.file.Path;
import java.time.Duration;

public interface ObjectStorageClient {

  String presignPut(String bucket, String objectKey, Duration ttl);

  String presignGet(String bucket, String objectKey, Duration ttl);

  ObjectStat statObject(String bucket, String objectKey);

  void downloadObject(String bucket, String objectKey, Path destination);

  void uploadObject(String bucket, String objectKey, Path source, String contentType);
}
