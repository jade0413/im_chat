package com.im.user.dao.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.im.common.mybatis.MybatisPlusConfig;
import com.im.common.mybatis.TenantLineHandlerConfig;
import com.im.common.tenant.TenantContext;
import com.im.common.test.IntegrationTestSupport;
import com.im.user.dao.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = {MybatisPlusConfig.class, TenantLineHandlerConfig.class})
@Testcontainers(disabledWithoutDocker = true)
class UserMapperIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private UserMapper userMapper;

  @Test
  void insertsUpdatesAndSelectsUserWithinTenant() {
    long userId = nextId();
    String account = "mapper_user_" + userId;

    TenantContext.runWithTenant(1L, () -> {
      UserEntity user = new UserEntity();
      user.setId(userId);
      user.setAccount(account);
      user.setPasswordHash("bcrypt-hash");
      user.setNickname("before");
      user.setAvatar("");
      user.setUserType(1);
      user.setVerifiedType(0);
      user.setStatus(1);

      assertThat(userMapper.insert(user)).isEqualTo(1);

      UserEntity patch = new UserEntity();
      patch.setId(userId);
      patch.setNickname("after");
      assertThat(userMapper.updateById(patch)).isEqualTo(1);

      UserEntity selected = userMapper.selectById(userId);
      assertThat(selected).isNotNull();
      assertThat(selected.getTenantId()).isEqualTo(1L);
      assertThat(selected.getAccount()).isEqualTo(account);
      assertThat(selected.getNickname()).isEqualTo("after");
    });

    TenantContext.runWithTenant(2L, () -> assertThat(userMapper.selectById(userId)).isNull());
  }

  private long nextId() {
    return 10_000_000_000L + System.nanoTime();
  }
}
