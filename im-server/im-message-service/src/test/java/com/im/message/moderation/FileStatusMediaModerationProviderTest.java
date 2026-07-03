package com.im.message.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.im.common.file.FileMetaConstants;
import com.im.message.dao.entity.MessageFileMetaEntity;
import com.im.message.dao.mapper.MessageFileMetaMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileStatusMediaModerationProviderTest {

  @Mock
  private MessageFileMetaMapper fileMetaMapper;

  @Test
  void flagsRejectedFileMeta() {
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a.png"))
        .thenReturn(fileMeta(FileMetaConstants.STATUS_REJECTED));

    var result = new FileStatusMediaModerationProvider(fileMetaMapper)
        .scan(new MediaModerationRequest(
            1L, 9003L, "1/202606/a.png", "image/png", 100L, "image"));

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().provider()).isEqualTo(ModerationConstants.PROVIDER_FILE_STATUS);
    assertThat(result.orElseThrow().category()).isEqualTo("image");
    assertThat(result.orElseThrow().evidence()).isEqualTo("image:1/202606/a.png");
  }

  @Test
  void ignoresConfirmedFileMeta() {
    when(fileMetaMapper.selectByObjectKey(1L, "1/202606/a.png"))
        .thenReturn(fileMeta(FileMetaConstants.STATUS_CONFIRMED));

    var result = new FileStatusMediaModerationProvider(fileMetaMapper)
        .scan(new MediaModerationRequest(
            1L, 9003L, "1/202606/a.png", "image/png", 100L, "image"));

    assertThat(result).isEmpty();
  }

  private MessageFileMetaEntity fileMeta(int status) {
    MessageFileMetaEntity meta = new MessageFileMetaEntity();
    meta.setTenantId(1L);
    meta.setObjectKey("1/202606/a.png");
    meta.setStatus(status);
    return meta;
  }
}
