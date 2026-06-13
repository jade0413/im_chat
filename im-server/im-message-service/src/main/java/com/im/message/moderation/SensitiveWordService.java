package com.im.message.moderation;

import com.im.message.moderation.dao.entity.SensitiveWordEntity;
import com.im.message.moderation.dao.mapper.SensitiveWordMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class SensitiveWordService {

  private final SensitiveWordMapper sensitiveWordMapper;
  private final Map<Long, List<SensitiveWord>> cache = new ConcurrentHashMap<>();

  public SensitiveWordService(SensitiveWordMapper sensitiveWordMapper) {
    this.sensitiveWordMapper = sensitiveWordMapper;
  }

  public Optional<ModerationMatch> findRevokeMatch(long tenantId, String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    String normalizedText = normalize(text);
    for (SensitiveWord word : wordsForTenant(tenantId)) {
      if (word.action() == ModerationConstants.WORD_ACTION_REVOKE
          && normalizedText.contains(word.word())) {
        return Optional.of(new ModerationMatch(word.word(), word.category(), word.action()));
      }
    }
    return Optional.empty();
  }

  public void reload(long tenantId) {
    if (tenantId <= 0) {
      cache.clear();
      return;
    }
    cache.put(tenantId, load(tenantId));
  }

  private List<SensitiveWord> wordsForTenant(long tenantId) {
    return cache.computeIfAbsent(tenantId, this::load);
  }

  private List<SensitiveWord> load(long tenantId) {
    return sensitiveWordMapper.selectEnabledForTenant(tenantId).stream()
        .filter(this::hasValidWord)
        .map(this::toWord)
        .toList();
  }

  private boolean hasValidWord(SensitiveWordEntity entity) {
    return entity.getWord() != null && !entity.getWord().isBlank()
        && Integer.valueOf(ModerationConstants.ENABLED).equals(entity.getEnabled());
  }

  private SensitiveWord toWord(SensitiveWordEntity entity) {
    return new SensitiveWord(
        normalize(entity.getWord()),
        entity.getCategory() == null || entity.getCategory().isBlank()
            ? "general"
            : entity.getCategory(),
        entity.getAction() == null ? ModerationConstants.WORD_ACTION_REVOKE : entity.getAction());
  }

  private String normalize(String value) {
    return value.toLowerCase(Locale.ROOT);
  }
}
