package com.im.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.auth.TokenVersionService;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.id.SnowflakeIdGenerator;
import com.im.common.tenant.TenantContext;
import com.im.user.config.AuthProperties;
import com.im.user.dao.entity.UserEntity;
import com.im.user.dao.mapper.UserMapper;
import com.im.user.dto.LoginRequest;
import com.im.user.dto.RegisterRequest;
import com.im.user.dto.TokenResponse;
import com.im.user.dto.UserProfileResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AuthServiceTest {

  @Mock
  private UserMapper userMapper;

  @Mock
  private TokenVersionService tokenVersionService;

  private AuthService authService;
  private JwtService jwtService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    AuthProperties properties = new AuthProperties(new AuthProperties.Jwt(
        "test-secret-test-secret-test-secret-32",
        "im-server-test",
        Duration.ofHours(2),
        Duration.ofDays(30)));
    jwtService = new JwtService(properties, new ObjectMapper());
    PasswordService passwordService = new PasswordService();
    when(tokenVersionService.nextVersion(eq(1L), anyLong(), eq("mobile"))).thenReturn(1L);
    authService = new AuthService(userMapper, passwordService, jwtService,
        new SnowflakeIdGenerator(1), tokenVersionService);
  }

  @Test
  void registerCreatesMemberAndReturnsTokens() throws Exception {
    when(userMapper.selectOne(any())).thenReturn(null);
    when(userMapper.insert(any(UserEntity.class))).thenReturn(1);

    TokenResponse response = TenantContext.callWithTenant(1L,
        () -> authService.register(new RegisterRequest(" alice ", "password123", " Alice ", 1)));

    assertThat(response.accessToken()).isNotBlank();
    assertThat(response.refreshToken()).isNotBlank();
    TokenClaims claims = jwtService.verifyAccessToken(response.accessToken());
    assertThat(claims.tenantId()).isEqualTo(1L);
    assertThat(claims.platformClass()).isEqualTo("mobile");
    assertThat(claims.tokenVersion()).isEqualTo(1L);

    ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userMapper).insert(captor.capture());
    UserEntity inserted = captor.getValue();
    assertThat(inserted.getTenantId()).isEqualTo(1L);
    assertThat(inserted.getAccount()).isEqualTo("alice");
    assertThat(inserted.getNickname()).isEqualTo("Alice");
    assertThat(inserted.getPasswordHash()).isNotEqualTo("password123");
    assertThat(inserted.getUserType()).isEqualTo(1);
    assertThat(inserted.getVerifiedType()).isZero();
    assertThat(inserted.getStatus()).isEqualTo(1);
  }

  @Test
  void registerRejectsDuplicateAccount() {
    when(userMapper.selectOne(any())).thenReturn(normalUser(101L, "alice", "hash"));

    assertThatThrownBy(() -> TenantContext.runWithTenant(1L,
        () -> authService.register(new RegisterRequest("alice", "password123", "Alice", 1))))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void loginReturnsTokensForValidPassword() throws Exception {
    PasswordService passwordService = new PasswordService();
    UserEntity user = normalUser(101L, "alice", passwordService.hash("password123"));
    when(userMapper.selectOne(any())).thenReturn(user);

    TokenResponse response = TenantContext.callWithTenant(1L,
        () -> authService.login(new LoginRequest("alice", "password123", 1)));

    TokenClaims claims = jwtService.verifyAccessToken(response.accessToken());
    assertThat(claims.tenantId()).isEqualTo(1L);
    assertThat(claims.userId()).isEqualTo(101L);
    assertThat(claims.platformClass()).isEqualTo("mobile");
    assertThat(claims.tokenVersion()).isEqualTo(1L);
  }

  @Test
  void loginFailureDoesNotRevealAccountExistence() {
    when(userMapper.selectOne(any())).thenReturn(null);

    assertThatThrownBy(() -> TenantContext.runWithTenant(1L,
        () -> authService.login(new LoginRequest("missing", "password123", 1))))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.TOKEN_INVALID);
  }

  @Test
  void loginRejectsBannedUser() {
    PasswordService passwordService = new PasswordService();
    UserEntity user = normalUser(101L, "alice", passwordService.hash("password123"));
    user.setStatus(3);
    when(userMapper.selectOne(any())).thenReturn(user);

    assertThatThrownBy(() -> TenantContext.runWithTenant(1L,
        () -> authService.login(new LoginRequest("alice", "password123", 1))))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.USER_BANNED);
  }

  @Test
  void refreshRotatesTokens() throws Exception {
    when(userMapper.selectById(101L)).thenReturn(normalUser(101L, "alice", "hash"));
    String refreshToken = jwtService.createRefreshToken(1L, 101L, "mobile", 1L);

    TokenResponse response = TenantContext.callWithTenant(1L, () -> authService.refresh(refreshToken));

    assertThat(jwtService.verifyAccessToken(response.accessToken()).userId()).isEqualTo(101L);
    TokenClaims refreshClaims = jwtService.verifyRefreshToken(response.refreshToken());
    assertThat(refreshClaims.userId()).isEqualTo(101L);
    assertThat(refreshClaims.platformClass()).isEqualTo("mobile");
    assertThat(refreshClaims.tokenVersion()).isEqualTo(1L);
  }

  @Test
  void currentUserReturnsProfile() throws Exception {
    when(userMapper.selectById(101L)).thenReturn(normalUser(101L, "alice", "hash"));
    String accessToken = jwtService.createAccessToken(1L, 101L, "mobile", 1L);

    UserProfileResponse response = TenantContext.callWithTenant(1L,
        () -> authService.currentUser(accessToken));

    assertThat(response.id()).isEqualTo(101L);
    assertThat(response.tenantId()).isEqualTo(1L);
    assertThat(response.account()).isEqualTo("alice");
  }

  @Test
  void rejectsCrossTenantTokenUse() {
    String accessToken = jwtService.createAccessToken(1L, 101L, "mobile", 1L);

    assertThatThrownBy(() -> TenantContext.runWithTenant(2L,
        () -> authService.currentUser(accessToken)))
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.TOKEN_INVALID);
  }

  private UserEntity normalUser(long id, String account, String passwordHash) {
    UserEntity user = new UserEntity();
    user.setId(id);
    user.setTenantId(1L);
    user.setAccount(account);
    user.setPasswordHash(passwordHash);
    user.setNickname(account);
    user.setAvatar("");
    user.setUserType(1);
    user.setVerifiedType(0);
    user.setStatus(1);
    return user;
  }
}
