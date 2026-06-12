package com.im.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.im.common.auth.TokenVersionService;
import com.im.proto.rpc.ConnCtx;
import com.im.proto.rpc.PushEnvelope;
import com.im.proto.ws.Cmd;
import com.im.proto.ws.KickNotify;
import com.im.push.config.PushProperties;
import com.im.push.route.OnlineRoute;
import com.im.push.route.OnlineRouteRepository;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushDispatchServiceTest {

  @Mock
  private OnlineRouteRepository routeRepository;

  @Mock
  private GatewayPushPublisher gatewayPushPublisher;

  @Mock
  private TokenVersionService tokenVersionService;

  private PushDispatchService service;

  @BeforeEach
  void setUp() {
    service = new PushDispatchService(routeRepository, gatewayPushPublisher,
        new PushProperties(Duration.ofSeconds(90), Duration.ofHours(24), "push.gw.", "im.push.msg.saved"),
        tokenVersionService);
  }

  @Test
  void groupsPushEnvelopeByGatewayInstance() {
    when(routeRepository.findAllByUsers(1L, List.of(100L, 200L, 300L, 400L)))
        .thenReturn(List.of(
            route(100L, "gw-a", "conn-a"),
            route(200L, "gw-a", "conn-b"),
            route(300L, "gw-b", "conn-c")));

    PushResult result = service.pushToUsers(1L, List.of(100L, 200L, 300L, 400L),
        Cmd.MSG_PUSH_VALUE, "body".getBytes(), true);

    assertThat(result.onlineCount()).isEqualTo(3);
    assertThat(result.offlineCount()).isEqualTo(1);
    ArgumentCaptor<PushEnvelope> envelopeCaptor = ArgumentCaptor.forClass(PushEnvelope.class);
    verify(gatewayPushPublisher).publish(org.mockito.Mockito.eq("gw-a"), envelopeCaptor.capture());
    PushEnvelope gwAEnvelope = envelopeCaptor.getValue();
    assertThat(gwAEnvelope.getTargetsList()).hasSize(2);
    assertThat(gwAEnvelope.getCmd()).isEqualTo(Cmd.MSG_PUSH_VALUE);
    assertThat(gwAEnvelope.getNeedAck()).isTrue();
  }

  @Test
  void normalizesDuplicateAndInvalidTargetsBeforeBatchLookup() {
    when(routeRepository.findAllByUsers(1L, List.of(100L, 200L)))
        .thenReturn(List.of(route(100L, "gw-a", "conn-a")));

    PushResult result = service.pushToUsers(1L, Arrays.asList(null, -1L, 100L, 100L, 200L),
        Cmd.MSG_PUSH_VALUE, null, false);

    assertThat(result.onlineCount()).isEqualTo(1);
    assertThat(result.offlineCount()).isEqualTo(1);
    verify(routeRepository).findAllByUsers(1L, List.of(100L, 200L));
  }

  @Test
  void connectedRouteKicksPreviousConnectionOfSamePlatformClassThenSavesNewRoute() throws Exception {
    OnlineRoute oldRoute = route(100L, "gw-a", "old-conn");
    ConnCtx newCtx = ctx("new-conn", "gw-b");
    when(routeRepository.find(1L, 100L, 1)).thenReturn(Optional.of(oldRoute));

    service.onConnected(newCtx);

    ArgumentCaptor<PushEnvelope> envelopeCaptor = ArgumentCaptor.forClass(PushEnvelope.class);
    verify(gatewayPushPublisher).publish(org.mockito.Mockito.eq("gw-a"), envelopeCaptor.capture());
    PushEnvelope envelope = envelopeCaptor.getValue();
    assertThat(envelope.getCmd()).isEqualTo(Cmd.KICK_VALUE);
    KickNotify kick = KickNotify.parseFrom(envelope.getBody());
    assertThat(kick.getReason()).isEqualTo(KickNotify.Reason.NEW_DEVICE_LOGIN);
    verify(routeRepository).save(org.mockito.Mockito.argThat(route -> route.connId().equals("new-conn")),
        org.mockito.Mockito.eq(Duration.ofSeconds(90)));
  }

  @Test
  void explicitKickInvalidatesTokenVersionAndPushesKick() {
    when(routeRepository.find(1L, 100L, 1)).thenReturn(Optional.of(route(100L, "gw-a", "conn-a")));

    PushResult result = service.kickUser(1L, 100L, 1, KickNotify.Reason.ADMIN_OFFLINE_VALUE);

    assertThat(result.onlineCount()).isEqualTo(1);
    verify(tokenVersionService).nextVersion(1L, 100L, "mobile");
    verify(gatewayPushPublisher).publish(org.mockito.Mockito.eq("gw-a"), org.mockito.Mockito.any());
  }

  private OnlineRoute route(long userId, String gwInstance, String connId) {
    return new OnlineRoute(1L, userId, 1, "mobile", "device-" + userId, connId, gwInstance);
  }

  private ConnCtx ctx(String connId, String gwInstance) {
    return ConnCtx.newBuilder()
        .setTenantId(1L)
        .setUserId(100L)
        .setPlatform(1)
        .setDeviceId("device-new")
        .setConnId(connId)
        .setGwInstance(gwInstance)
        .build();
  }
}
