package com.im.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import org.junit.jupiter.api.Test;

class C2cKeyGeneratorTest {

  private final C2cKeyGenerator generator = new C2cKeyGenerator();

  @Test
  void generatesStableOrderedKey() {
    assertThat(generator.generate(200L, 100L)).isEqualTo("100_200");
    assertThat(generator.generate(100L, 200L)).isEqualTo("100_200");
  }

  @Test
  void rejectsInvalidUserIds() {
    assertThatThrownBy(() -> generator.generate(0L, 100L))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void rejectsSelfConversation() {
    assertThatThrownBy(() -> generator.generate(100L, 100L))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }
}
