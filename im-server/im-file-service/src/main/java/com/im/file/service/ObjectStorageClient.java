package com.im.file.service;

import java.time.Duration;

public interface ObjectStorageClient {

  String presignPut(String bucket, String objectKey, Duration ttl);

  String presignGet(String bucket, String objectKey, Duration ttl);

  ObjectStat statObject(String bucket, String objectKey);
}
