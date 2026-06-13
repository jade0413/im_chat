package com.im.file.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class MinioObjectStorageClient implements ObjectStorageClient {

  private final MinioClient minioClient;

  public MinioObjectStorageClient(MinioClient minioClient) {
    this.minioClient = minioClient;
  }

  @Override
  public String presignPut(String bucket, String objectKey, Duration ttl) {
    try {
      return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
          .method(Method.PUT)
          .bucket(bucket)
          .object(objectKey)
          .expiry(Math.toIntExact(ttl.toSeconds()), TimeUnit.SECONDS)
          .build());
    } catch (Exception ex) {
      throw new ObjectStorageException("failed to create presigned url", ex, false);
    }
  }

  @Override
  public ObjectStat statObject(String bucket, String objectKey) {
    try {
      StatObjectResponse response = minioClient.statObject(StatObjectArgs.builder()
          .bucket(bucket)
          .object(objectKey)
          .build());
      return new ObjectStat(response.size(), response.contentType());
    } catch (ErrorResponseException ex) {
      throw new ObjectStorageException("object storage stat failed", ex, isNotFound(ex));
    } catch (Exception ex) {
      throw new ObjectStorageException("object storage stat failed", ex, false);
    }
  }

  private boolean isNotFound(ErrorResponseException ex) {
    String code = ex.errorResponse() == null ? "" : ex.errorResponse().code();
    return "NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NoSuchBucket".equals(code);
  }
}
