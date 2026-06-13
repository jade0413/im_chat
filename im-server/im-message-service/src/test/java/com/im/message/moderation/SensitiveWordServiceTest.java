package com.im.message.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.im.message.moderation.dao.entity.SensitiveWordEntity;
import com.im.message.moderation.dao.mapper.SensitiveWordMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SensitiveWordServiceTest {

  @Mock
  private SensitiveWordMapper sensitiveWordMapper;

  @Test
  void findsTenantOrPlatformRevokeWordCaseInsensitive() {
    when(sensitiveWordMapper.selectEnabledForTenant(1L))
        .thenReturn(List.of(word(null, "BadWord", "abuse"), word(1L, "risk", "risk")));

    SensitiveWordService service = new SensitiveWordService(sensitiveWordMapper);

    assertThat(service.findRevokeMatch(1L, "this has a badword").orElseThrow().category())
        .isEqualTo("abuse");
    assertThat(service.findRevokeMatch(1L, "RISK text").orElseThrow().category())
        .isEqualTo("risk");
  }

  @Test
  void reloadTenantRefreshesCachedWords() {
    when(sensitiveWordMapper.selectEnabledForTenant(1L))
        .thenReturn(List.of(word(null, "old", "general")))
        .thenReturn(List.of(word(null, "new", "general")));

    SensitiveWordService service = new SensitiveWordService(sensitiveWordMapper);

    assertThat(service.findRevokeMatch(1L, "old").isPresent()).isTrue();
    service.reload(1L);

    assertThat(service.findRevokeMatch(1L, "old").isPresent()).isFalse();
    assertThat(service.findRevokeMatch(1L, "new").isPresent()).isTrue();
    verify(sensitiveWordMapper, org.mockito.Mockito.times(2)).selectEnabledForTenant(1L);
  }

  private SensitiveWordEntity word(Long tenantId, String word, String category) {
    SensitiveWordEntity entity = new SensitiveWordEntity();
    entity.setTenantId(tenantId);
    entity.setWord(word);
    entity.setCategory(category);
    entity.setAction(ModerationConstants.WORD_ACTION_REVOKE);
    entity.setEnabled(ModerationConstants.ENABLED);
    return entity;
  }
}
