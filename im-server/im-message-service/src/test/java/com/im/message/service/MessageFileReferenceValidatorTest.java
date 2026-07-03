package com.im.message.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.file.FileMetaConstants;
import com.im.message.dao.entity.MessageFileMetaEntity;
import com.im.message.dao.mapper.MessageFileMetaMapper;
import com.im.proto.common.FileContent;
import com.im.proto.common.ImageContent;
import com.im.proto.common.MsgContent;
import com.im.proto.common.TextContent;
import com.im.proto.common.VoiceContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageFileReferenceValidatorTest {

  @Mock
  private MessageFileMetaMapper fileMetaMapper;

  @Test
  void acceptsConfirmedImageFromSameTenant() {
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a.png"))
        .thenReturn(fileMeta("1/202606/a.png", "image/png", 512L, FileMetaConstants.STATUS_CONFIRMED));

    assertThatCode(() -> new MessageFileReferenceValidator(fileMetaMapper)
        .ensureReferencesConfirmed(1L, MsgContent.newBuilder()
            .setImage(ImageContent.newBuilder()
                .setObjectKey("1/202606/a.png")
                .setMime("image/png")
                .setSize(512L))
            .build()))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsConfirmedImageWithThumbnail() {
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a.png"))
        .thenReturn(fileMeta("1/202606/a.png", "image/png", 512L, FileMetaConstants.STATUS_CONFIRMED));
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a-thumb.jpg"))
        .thenReturn(fileMeta("1/202606/a-thumb.jpg", "image/jpeg", 128L, FileMetaConstants.STATUS_CONFIRMED));

    assertThatCode(() -> new MessageFileReferenceValidator(fileMetaMapper)
        .ensureReferencesConfirmed(1L, MsgContent.newBuilder()
            .setImage(ImageContent.newBuilder()
                .setObjectKey("1/202606/a.png")
                .setThumbKey("1/202606/a-thumb.jpg")
                .setMime("image/png")
                .setSize(512L))
            .build()))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsUnconfirmedFile() {
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a.pdf"))
        .thenReturn(fileMeta("1/202606/a.pdf", "application/pdf", 512L, FileMetaConstants.STATUS_PENDING));

    assertThatThrownBy(() -> new MessageFileReferenceValidator(fileMetaMapper)
        .ensureReferencesConfirmed(1L, MsgContent.newBuilder()
            .setFile(FileContent.newBuilder()
                .setObjectKey("1/202606/a.pdf")
                .setFileName("a.pdf")
                .setMime("application/pdf")
                .setSize(512L))
            .build()))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void rejectsCrossTenantObjectKeyBeforeQuerying() {
    assertThatThrownBy(() -> new MessageFileReferenceValidator(fileMetaMapper)
        .ensureReferencesConfirmed(1L, MsgContent.newBuilder()
            .setImage(ImageContent.newBuilder()
                .setObjectKey("2/202606/a.png")
                .setMime("image/png")
                .setSize(512L))
            .build()))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void acceptsTextWithoutFileLookup() {
    assertThatCode(() -> new MessageFileReferenceValidator(fileMetaMapper)
        .ensureReferencesConfirmed(1L, MsgContent.newBuilder()
            .setText(TextContent.newBuilder().setText("hello"))
            .build()))
        .doesNotThrowAnyException();
  }

  @Test
  void validatesVoiceAsAudioFile() {
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a.aac"))
        .thenReturn(fileMeta("1/202606/a.aac", "audio/aac", 256L, FileMetaConstants.STATUS_CONFIRMED));

    assertThatCode(() -> new MessageFileReferenceValidator(fileMetaMapper)
        .ensureReferencesConfirmed(1L, MsgContent.newBuilder()
            .setVoice(VoiceContent.newBuilder()
                .setObjectKey("1/202606/a.aac")
                .setDurationMs(2000)
                .setSize(256L)
                .setCodec("aac"))
            .build()))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsConfirmedVideoFileWithThumbnail() {
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a.m4v"))
        .thenReturn(fileMeta("1/202606/a.m4v", "video/x-m4v", 4096L,
            FileMetaConstants.STATUS_CONFIRMED));
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a-thumb.jpg"))
        .thenReturn(fileMeta("1/202606/a-thumb.jpg", "image/jpeg", 128L,
            FileMetaConstants.STATUS_CONFIRMED));

    assertThatCode(() -> new MessageFileReferenceValidator(fileMetaMapper)
        .ensureReferencesConfirmed(1L, MsgContent.newBuilder()
            .setFile(FileContent.newBuilder()
                .setObjectKey("1/202606/a.m4v")
                .setFileName("clip.m4v")
                .setMime("video/x-m4v")
                .setSize(4096L)
                .setThumbKey("1/202606/a-thumb.jpg")
                .setDurationMs(12_000))
            .build()))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsVideoFileWhenThumbnailIsNotImage() {
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a.mp4"))
        .thenReturn(fileMeta("1/202606/a.mp4", "video/mp4", 4096L,
            FileMetaConstants.STATUS_CONFIRMED));
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a-thumb.pdf"))
        .thenReturn(fileMeta("1/202606/a-thumb.pdf", "application/pdf", 128L,
            FileMetaConstants.STATUS_CONFIRMED));

    assertThatThrownBy(() -> new MessageFileReferenceValidator(fileMetaMapper)
        .ensureReferencesConfirmed(1L, MsgContent.newBuilder()
            .setFile(FileContent.newBuilder()
                .setObjectKey("1/202606/a.mp4")
                .setFileName("clip.mp4")
                .setMime("video/mp4")
                .setSize(4096L)
                .setThumbKey("1/202606/a-thumb.pdf")
                .setDurationMs(12_000))
            .build()))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.MIME_NOT_ALLOWED);
  }

  @Test
  void rejectsFileMimeMismatch() {
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a.m4v"))
        .thenReturn(fileMeta("1/202606/a.m4v", "video/x-m4v", 4096L,
            FileMetaConstants.STATUS_CONFIRMED));

    assertThatThrownBy(() -> new MessageFileReferenceValidator(fileMetaMapper)
        .ensureReferencesConfirmed(1L, MsgContent.newBuilder()
            .setFile(FileContent.newBuilder()
                .setObjectKey("1/202606/a.m4v")
                .setFileName("clip.m4v")
                .setMime("video/mp4")
                .setSize(4096L))
            .build()))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.MIME_NOT_ALLOWED);
  }

  private MessageFileMetaEntity fileMeta(String objectKey, String mime, long size, int status) {
    MessageFileMetaEntity entity = new MessageFileMetaEntity();
    entity.setObjectKey(objectKey);
    entity.setMime(mime);
    entity.setSize(size);
    entity.setStatus(status);
    return entity;
  }
}
