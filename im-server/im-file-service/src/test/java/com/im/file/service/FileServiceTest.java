package com.im.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.file.FileMetaConstants;
import com.im.common.id.SnowflakeIdGenerator;
import com.im.common.tenant.TenantContext;
import com.im.file.config.FileProperties;
import com.im.file.dao.entity.FileMetaEntity;
import com.im.file.dao.mapper.FileMetaMapper;
import com.im.file.dto.ConfirmFileRequest;
import com.im.file.dto.FileMetaResponse;
import com.im.file.dto.PresignFileRequest;
import com.im.file.dto.PresignFileResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

  @Mock
  private FileMetaMapper fileMetaMapper;

  @Mock
  private ObjectStorageClient storageClient;

  @Mock
  private SnowflakeIdGenerator idGenerator;

  @Captor
  private ArgumentCaptor<FileMetaEntity> fileCaptor;

  private FileService service;

  @BeforeEach
  void setUp() {
    FileProperties properties = new FileProperties(
        "http://minio:9000",
        "ak",
        "sk",
        "im-media",
        Duration.ofMinutes(5),
        Set.of("image/png", "audio/aac", "application/pdf"),
        new FileProperties.SizeLimit(1024L, 2048L, 4096L, 8192L));
    service = new FileService(
        fileMetaMapper,
        storageClient,
        idGenerator,
        properties,
        transactionTemplate(),
        Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void presignCreatesTenantScopedPendingMetadata() throws Exception {
    when(idGenerator.nextId()).thenReturn(9001L);
    when(fileMetaMapper.insert(any(FileMetaEntity.class))).thenReturn(1);
    when(storageClient.presignPut(eq("im-media"), any(String.class), eq(Duration.ofMinutes(5))))
        .thenReturn("http://upload");

    PresignFileResponse response = TenantContext.callWithTenant(1L,
        () -> service.presign(100L, new PresignFileRequest("avatar.png", "image/png", 512L, null)));

    assertThat(response.fileId()).isEqualTo(9001L);
    assertThat(response.objectKey()).startsWith("1/202606/");
    assertThat(response.uploadUrl()).isEqualTo("http://upload");
    assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-06-13T00:05:00Z").toEpochMilli());
    assertThat(response.requiredHeaders()).containsEntry("Content-Type", "image/png");

    org.mockito.Mockito.verify(fileMetaMapper).insert(fileCaptor.capture());
    assertThat(fileCaptor.getValue().getTenantId()).isEqualTo(1L);
    assertThat(fileCaptor.getValue().getUploaderId()).isEqualTo(100L);
    assertThat(fileCaptor.getValue().getStatus()).isEqualTo(FileMetaConstants.STATUS_PENDING);
    assertThat(fileCaptor.getValue().getMime()).isEqualTo("image/png");
  }

  @Test
  void presignRejectsDisallowedMime() {
    assertThatThrownBy(() -> TenantContext.callWithTenant(1L,
        () -> service.presign(100L, new PresignFileRequest("x.exe", "application/x-msdownload", 10L, null))))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.MIME_NOT_ALLOWED);
  }

  @Test
  void presignRejectsOversizedFile() {
    assertThatThrownBy(() -> TenantContext.callWithTenant(1L,
        () -> service.presign(100L, new PresignFileRequest("avatar.png", "image/png", 2048L, null))))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.FILE_TOO_LARGE);
  }

  @Test
  void confirmStatsObjectAndMarksMetadataConfirmed() throws Exception {
    FileMetaEntity meta = pendingMeta();
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a.png")).thenReturn(meta);
    when(storageClient.statObject("im-media", "1/202606/a.png"))
        .thenReturn(new ObjectStat(512L, "image/png"));
    when(fileMetaMapper.updateStatus(1L, "1/202606/a.png", FileMetaConstants.STATUS_PENDING,
        FileMetaConstants.STATUS_CONFIRMED)).thenReturn(1);

    FileMetaResponse response = TenantContext.callWithTenant(1L,
        () -> service.confirm(100L, new ConfirmFileRequest("1/202606/a.png", 512L, "image/png")));

    assertThat(response.fileId()).isEqualTo(9001L);
    assertThat(response.status()).isEqualTo(FileMetaConstants.STATUS_CONFIRMED);
  }

  @Test
  void confirmIsIdempotentWhenAlreadyConfirmed() throws Exception {
    FileMetaEntity meta = pendingMeta();
    meta.setStatus(FileMetaConstants.STATUS_CONFIRMED);
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a.png")).thenReturn(meta);

    FileMetaResponse response = TenantContext.callWithTenant(1L,
        () -> service.confirm(100L, new ConfirmFileRequest("1/202606/a.png", null, null)));

    assertThat(response.status()).isEqualTo(FileMetaConstants.STATUS_CONFIRMED);
  }

  @Test
  void confirmRejectsCrossTenantObjectKey() {
    assertThatThrownBy(() -> TenantContext.callWithTenant(1L,
        () -> service.confirm(100L, new ConfirmFileRequest("2/202606/a.png", null, null))))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }

  private FileMetaEntity pendingMeta() {
    FileMetaEntity entity = new FileMetaEntity();
    entity.setId(9001L);
    entity.setTenantId(1L);
    entity.setUploaderId(100L);
    entity.setObjectKey("1/202606/a.png");
    entity.setMime("image/png");
    entity.setSize(512L);
    entity.setStatus(FileMetaConstants.STATUS_PENDING);
    return entity;
  }

  private TransactionTemplate transactionTemplate() {
    return new TransactionTemplate(new PlatformTransactionManager() {
      @Override
      public TransactionStatus getTransaction(TransactionDefinition definition) {
        return new SimpleTransactionStatus();
      }

      @Override
      public void commit(TransactionStatus status) {
      }

      @Override
      public void rollback(TransactionStatus status) {
      }
    });
  }
}
