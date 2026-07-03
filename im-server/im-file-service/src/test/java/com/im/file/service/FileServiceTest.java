package com.im.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.im.file.dto.DownloadFileResponse;
import com.im.file.dto.FileMetaResponse;
import com.im.file.dto.PresignFileRequest;
import com.im.file.dto.PresignFileResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
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
  private static final String SHA256 =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  @Mock
  private FileMetaMapper fileMetaMapper;

  @Mock
  private ObjectStorageClient storageClient;

  @Mock
  private SnowflakeIdGenerator idGenerator;

  @Mock
  private FileTranscodeJobService transcodeJobService;

  @Captor
  private ArgumentCaptor<FileMetaEntity> fileCaptor;

  private FileService service;

  @BeforeEach
  void setUp() {
    service = newService(defaultProperties());
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
  void presignAcceptsDefaultClientVideoMimeAndAddsMimeExtensionWhenNameHasNone() throws Exception {
    FileService defaultService = newService(propertiesWithDefaultAllowedMimes());
    when(idGenerator.nextId()).thenReturn(9002L);
    when(fileMetaMapper.insert(any(FileMetaEntity.class))).thenReturn(1);
    when(storageClient.presignPut(eq("im-media"), any(String.class), eq(Duration.ofMinutes(5))))
        .thenReturn("http://upload-m4v");

    PresignFileResponse response = TenantContext.callWithTenant(1L,
        () -> defaultService.presign(100L, new PresignFileRequest("clip", "video/x-m4v", 4096L, 12000)));

    assertThat(response.fileId()).isEqualTo(9002L);
    assertThat(response.objectKey()).startsWith("1/202606/");
    assertThat(response.objectKey()).endsWith(".m4v");
    assertThat(response.requiredHeaders()).containsEntry("Content-Type", "video/x-m4v");
    org.mockito.Mockito.verify(fileMetaMapper).insert(fileCaptor.capture());
    assertThat(fileCaptor.getValue().getMime()).isEqualTo("video/x-m4v");
    assertThat(fileCaptor.getValue().getDurationMs()).isEqualTo(12000);
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
  void presignReturnsInstantResultWhenSameUploaderFileExists() throws Exception {
    FileMetaEntity meta = pendingMeta();
    meta.setStatus(FileMetaConstants.STATUS_CONFIRMED);
    meta.setSha256(SHA256);
    when(fileMetaMapper.selectConfirmedByUploaderHash(
        1L, 100L, SHA256, 512L, "image/png", FileMetaConstants.STATUS_CONFIRMED))
        .thenReturn(meta);

    PresignFileResponse response = TenantContext.callWithTenant(1L,
        () -> service.presign(100L,
            new PresignFileRequest("again.png", "image/png", 512L, null, SHA256)));

    assertThat(response.instant()).isTrue();
    assertThat(response.objectKey()).isEqualTo("1/202606/a.png");
    assertThat(response.uploadUrl()).isEmpty();
    verify(fileMetaMapper, never()).insert(any(FileMetaEntity.class));
    verify(storageClient, never()).presignPut(eq("im-media"), any(String.class), any(Duration.class));
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
    verify(transcodeJobService).enqueueAfterConfirm(meta);
  }

  @Test
  void confirmAllowsGenericObjectStorageContentType() throws Exception {
    FileMetaEntity meta = pendingMeta();
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a.png")).thenReturn(meta);
    when(storageClient.statObject("im-media", "1/202606/a.png"))
        .thenReturn(new ObjectStat(512L, "application/octet-stream"));
    when(fileMetaMapper.updateStatus(1L, "1/202606/a.png", FileMetaConstants.STATUS_PENDING,
        FileMetaConstants.STATUS_CONFIRMED)).thenReturn(1);

    FileMetaResponse response = TenantContext.callWithTenant(1L,
        () -> service.confirm(100L, new ConfirmFileRequest("1/202606/a.png", 512L, "image/png")));

    assertThat(response.status()).isEqualTo(FileMetaConstants.STATUS_CONFIRMED);
    verify(transcodeJobService).enqueueAfterConfirm(meta);
  }

  @Test
  void confirmEnqueuesVideoTranscodeJobAfterMetadataConfirmed() throws Exception {
    FileMetaEntity meta = pendingMeta();
    meta.setObjectKey("1/202606/a.mp4");
    meta.setMime("video/mp4");
    meta.setSize(4096L);
    meta.setDurationMs(12_000);
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a.mp4")).thenReturn(meta);
    when(storageClient.statObject("im-media", "1/202606/a.mp4"))
        .thenReturn(new ObjectStat(4096L, "application/octet-stream"));
    when(fileMetaMapper.updateStatus(1L, "1/202606/a.mp4", FileMetaConstants.STATUS_PENDING,
        FileMetaConstants.STATUS_CONFIRMED)).thenReturn(1);

    FileMetaResponse response = TenantContext.callWithTenant(1L,
        () -> service.confirm(100L, new ConfirmFileRequest("1/202606/a.mp4", 4096L, "video/mp4")));

    assertThat(response.status()).isEqualTo(FileMetaConstants.STATUS_CONFIRMED);
    verify(transcodeJobService).enqueueAfterConfirm(meta);
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

  @Test
  void presignDownloadUsesConfiguredDownloadTtlByDefault() throws Exception {
    FileMetaEntity meta = pendingMeta();
    meta.setStatus(FileMetaConstants.STATUS_CONFIRMED);
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a.png")).thenReturn(meta);
    when(storageClient.presignGet("im-media", "1/202606/a.png", Duration.ofMinutes(15)))
        .thenReturn("http://download");

    String url = TenantContext.callWithTenant(1L,
        () -> service.presignDownload(100L, "1/202606/a.png"));

    assertThat(url).isEqualTo("http://download");
    verify(storageClient).presignGet("im-media", "1/202606/a.png", Duration.ofMinutes(15));
  }

  @Test
  void presignPlaybackDownloadUsesSucceededTranscodeTargetForVideo() throws Exception {
    FileMetaEntity meta = pendingMeta();
    meta.setObjectKey("1/202606/a.mov");
    meta.setMime("video/quicktime");
    meta.setStatus(FileMetaConstants.STATUS_CONFIRMED);
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a.mov")).thenReturn(meta);
    when(transcodeJobService.succeededTargetObjectKey(
        1L,
        9001L,
        FileTranscodeConstants.PROFILE_MP4_720P))
        .thenReturn(Optional.of("1/202606/transcoded/a_mp4_720p.mp4"));
    when(storageClient.presignGet(
        "im-media",
        "1/202606/transcoded/a_mp4_720p.mp4",
        Duration.ofMinutes(15)))
        .thenReturn("http://playback");

    String url = TenantContext.callWithTenant(1L,
        () -> service.presignDownload(100L, "1/202606/a.mov", "playback"));

    assertThat(url).isEqualTo("http://playback");
    verify(storageClient).presignGet(
        "im-media",
        "1/202606/transcoded/a_mp4_720p.mp4",
        Duration.ofMinutes(15));
  }

  @Test
  void presignDownloadInfoReturnsResolvedPlaybackObjectKey() throws Exception {
    FileMetaEntity meta = pendingMeta();
    meta.setObjectKey("1/202606/a.mov");
    meta.setMime("video/quicktime");
    meta.setStatus(FileMetaConstants.STATUS_CONFIRMED);
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a.mov")).thenReturn(meta);
    when(transcodeJobService.succeededTargetObjectKey(
        1L,
        9001L,
        FileTranscodeConstants.PROFILE_MP4_720P))
        .thenReturn(Optional.of("1/202606/transcoded/a_mp4_720p.mp4"));
    when(storageClient.presignGet(
        "im-media",
        "1/202606/transcoded/a_mp4_720p.mp4",
        Duration.ofMinutes(15)))
        .thenReturn("http://playback");

    DownloadFileResponse response = TenantContext.callWithTenant(1L,
        () -> service.presignDownloadInfo(100L, "1/202606/a.mov", "playback"));

    assertThat(response.objectKey()).isEqualTo("1/202606/transcoded/a_mp4_720p.mp4");
    assertThat(response.url()).isEqualTo("http://playback");
    assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-06-13T00:15:00Z").toEpochMilli());
    assertThat(response.transformed()).isTrue();
  }

  @Test
  void presignPlaybackDownloadFallsBackToOriginalWhenTranscodeIsNotReady() throws Exception {
    FileMetaEntity meta = pendingMeta();
    meta.setObjectKey("1/202606/a.mov");
    meta.setMime("video/quicktime");
    meta.setStatus(FileMetaConstants.STATUS_CONFIRMED);
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a.mov")).thenReturn(meta);
    when(transcodeJobService.succeededTargetObjectKey(
        1L,
        9001L,
        FileTranscodeConstants.PROFILE_MP4_720P))
        .thenReturn(Optional.empty());
    when(storageClient.presignGet("im-media", "1/202606/a.mov", Duration.ofMinutes(15)))
        .thenReturn("http://original");

    String url = TenantContext.callWithTenant(1L,
        () -> service.presignDownload(100L, "1/202606/a.mov", "playback"));

    assertThat(url).isEqualTo("http://original");
    verify(storageClient).presignGet("im-media", "1/202606/a.mov", Duration.ofMinutes(15));
  }

  @Test
  void presignDownloadReturnsCdnUrlWhenConfigured() throws Exception {
    FileService cdnService = newService(propertiesWithCdn("https://cdn.example.com/media/"));
    FileMetaEntity meta = pendingMeta();
    meta.setObjectKey("1/202606/a file.png");
    meta.setStatus(FileMetaConstants.STATUS_CONFIRMED);
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a file.png")).thenReturn(meta);

    String url = TenantContext.callWithTenant(1L,
        () -> cdnService.presignDownload(100L, "1/202606/a file.png"));

    assertThat(url).isEqualTo("https://cdn.example.com/media/1/202606/a%20file.png");
    verify(storageClient, never()).presignGet(any(String.class), any(String.class), any(Duration.class));
  }

  private FileService newService(FileProperties properties) {
    return new FileService(
        fileMetaMapper,
        storageClient,
        idGenerator,
        transcodeJobService,
        properties,
        transactionTemplate(),
        Clock.fixed(Instant.parse("2026-06-13T00:00:00Z"), ZoneOffset.UTC));
  }

  private FileProperties defaultProperties() {
    return new FileProperties(
        "http://minio:9000",
        "http://localhost:9000",
        "ak",
        "sk",
        "im-media",
        Duration.ofMinutes(5),
        Set.of("image/png", "audio/aac", "application/pdf"),
        new FileProperties.SizeLimit(1024L, 2048L, 4096L, 8192L));
  }

  private FileProperties propertiesWithCdn(String cdnBaseUrl) {
    return new FileProperties(
        "http://minio:9000",
        "http://localhost:9000",
        "ak",
        "sk",
        "im-media",
        Duration.ofMinutes(5),
        Duration.ofMinutes(30),
        cdnBaseUrl,
        Set.of("image/png", "audio/aac", "application/pdf"),
        new FileProperties.SizeLimit(1024L, 2048L, 4096L, 8192L));
  }

  private FileProperties propertiesWithDefaultAllowedMimes() {
    return new FileProperties(
        "http://minio:9000",
        "http://localhost:9000",
        "ak",
        "sk",
        "im-media",
        Duration.ofMinutes(5),
        null,
        new FileProperties.SizeLimit(1024L, 2048L, 8192L, 4096L));
  }

  private FileMetaEntity pendingMeta() {
    FileMetaEntity entity = new FileMetaEntity();
    entity.setId(9001L);
    entity.setTenantId(1L);
    entity.setUploaderId(100L);
    entity.setObjectKey("1/202606/a.png");
    entity.setMime("image/png");
    entity.setSize(512L);
    entity.setSha256(SHA256);
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
