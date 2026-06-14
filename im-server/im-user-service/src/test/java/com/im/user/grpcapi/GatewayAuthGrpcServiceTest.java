package com.im.user.grpcapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.auth.TokenVersionService;
import com.im.common.error.ErrorCode;
import com.im.proto.rpc.GatewayAuthGrpc;
import com.im.proto.rpc.VerifyTokenReq;
import com.im.proto.rpc.VerifyTokenResp;
import com.im.user.config.AuthProperties;
import com.im.user.config.GatewayAuthProperties;
import com.im.user.dao.entity.UserEntity;
import com.im.user.dao.mapper.UserMapper;
import com.im.user.service.JwtService;
import com.im.user.service.TokenVerifier;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class GatewayAuthGrpcServiceTest {

  @Mock
  private UserMapper userMapper;

  @Mock
  private TokenVersionService tokenVersionService;

  private JwtService jwtService;
  private Server server;
  private ManagedChannel channel;
  private GatewayAuthGrpc.GatewayAuthBlockingStub stub;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    AuthProperties properties = new AuthProperties(new AuthProperties.Jwt(
        "grpc-secret-grpc-secret-grpc-secret-32",
        "im-server-test",
        Duration.ofHours(2),
        Duration.ofDays(30)));
    jwtService = new JwtService(properties, new ObjectMapper());
    TokenVerifier tokenVerifier = new TokenVerifier(jwtService, userMapper,
        new GatewayAuthProperties(30), tokenVersionService);
    GatewayAuthGrpcService service = new GatewayAuthGrpcService(tokenVerifier);

    String serverName = InProcessServerBuilder.generateName() + UUID.randomUUID();
    server = InProcessServerBuilder.forName(serverName)
        .directExecutor()
        .addService(service)
        .build()
        .start();
    channel = InProcessChannelBuilder.forName(serverName)
        .directExecutor()
        .build();
    stub = GatewayAuthGrpc.newBlockingStub(channel);
  }

  @AfterEach
  void tearDown() {
    if (channel != null) {
      channel.shutdownNow();
    }
    if (server != null) {
      server.shutdownNow();
    }
  }

  @Test
  void verifiesValidAccessToken() {
    when(userMapper.selectById(101L)).thenReturn(normalUser(101L, 1));
    String token = jwtService.createAccessToken(1L, 101L, "mobile", 1L);

    VerifyTokenResp response = stub.verifyToken(VerifyTokenReq.newBuilder()
        .setToken(token)
        .setTenantId(1L)
        .setDeviceId("device-1")
        .setPlatform(1)
        .setGwInstance("gw-1")
        .build());

    assertThat(response.getCode()).isEqualTo(ErrorCode.OK.code());
    assertThat(response.getUserId()).isEqualTo(101L);
    assertThat(response.getHeartbeatIntervalSec()).isEqualTo(30);
    assertThat(response.getKickOld()).isFalse();
  }

  @Test
  void verifiesVisitorTokenWithoutPlatformClass() {
    when(userMapper.selectById(202L)).thenReturn(normalUser(202L, 1));
    String token = jwtService.createAccessToken(1L, 202L);

    VerifyTokenResp response = stub.verifyToken(VerifyTokenReq.newBuilder()
        .setToken(token)
        .setTenantId(1L)
        .setDeviceId("visitor-device")
        .setPlatform(5)
        .setGwInstance("gw-1")
        .build());

    assertThat(response.getCode()).isEqualTo(ErrorCode.OK.code());
    assertThat(response.getUserId()).isEqualTo(202L);
    org.mockito.Mockito.verify(tokenVersionService, org.mockito.Mockito.never())
        .ensureCurrent(org.mockito.Mockito.anyLong(), org.mockito.Mockito.anyLong(),
            org.mockito.Mockito.anyString(), org.mockito.Mockito.anyLong());
  }

  @Test
  void returnsTokenInvalidForMalformedToken() {
    VerifyTokenResp response = stub.verifyToken(VerifyTokenReq.newBuilder()
        .setToken("bad-token")
        .setTenantId(1L)
        .build());

    assertThat(response.getCode()).isEqualTo(ErrorCode.TOKEN_INVALID.code());
    assertThat(response.getUserId()).isZero();
  }

  @Test
  void returnsTokenInvalidForCrossTenantToken() {
    String token = jwtService.createAccessToken(1L, 101L, "mobile", 1L);

    VerifyTokenResp response = stub.verifyToken(VerifyTokenReq.newBuilder()
        .setToken(token)
        .setTenantId(2L)
        .build());

    assertThat(response.getCode()).isEqualTo(ErrorCode.TOKEN_INVALID.code());
  }

  @Test
  void returnsUserBannedForBannedUser() {
    UserEntity user = normalUser(101L, 1);
    user.setStatus(3);
    when(userMapper.selectById(101L)).thenReturn(user);
    String token = jwtService.createAccessToken(1L, 101L, "mobile", 1L);

    VerifyTokenResp response = stub.verifyToken(VerifyTokenReq.newBuilder()
        .setToken(token)
        .setTenantId(1L)
        .setPlatform(1)
        .build());

    assertThat(response.getCode()).isEqualTo(ErrorCode.USER_BANNED.code());
  }

  @Test
  void rejectsStaleTokenVersion() {
    org.mockito.Mockito.doThrow(new com.im.common.error.ImException(ErrorCode.TOKEN_INVALID))
        .when(tokenVersionService).ensureCurrent(1L, 101L, "mobile", 1L);
    String token = jwtService.createAccessToken(1L, 101L, "mobile", 1L);

    VerifyTokenResp response = stub.verifyToken(VerifyTokenReq.newBuilder()
        .setToken(token)
        .setTenantId(1L)
        .setPlatform(1)
        .build());

    assertThat(response.getCode()).isEqualTo(ErrorCode.TOKEN_INVALID.code());
  }

  private UserEntity normalUser(long userId, long tenantId) {
    UserEntity user = new UserEntity();
    user.setId(userId);
    user.setTenantId(tenantId);
    user.setAccount("user" + userId);
    user.setNickname("User " + userId);
    user.setAvatar("");
    user.setUserType(1);
    user.setVerifiedType(0);
    user.setStatus(1);
    return user;
  }
}
