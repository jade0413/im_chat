package com.im.push.service;

import com.google.protobuf.ByteString;
import com.im.common.auth.TokenVersionService;
import com.im.common.device.PlatformClass;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.trace.TraceContext;
import com.im.proto.rpc.ConnCtx;
import com.im.proto.rpc.PushEnvelope;
import com.im.proto.rpc.PushTarget;
import com.im.proto.ws.Cmd;
import com.im.proto.ws.KickNotify;
import com.im.push.config.PushProperties;
import com.im.push.route.OnlineRoute;
import com.im.push.route.OnlineRouteRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PushDispatchService {

  private final OnlineRouteRepository routeRepository;
  private final GatewayPushPublisher gatewayPushPublisher;
  private final PushProperties properties;
  private final TokenVersionService tokenVersionService;

  public PushDispatchService(OnlineRouteRepository routeRepository,
      GatewayPushPublisher gatewayPushPublisher,
      PushProperties properties,
      TokenVersionService tokenVersionService) {
    this.routeRepository = routeRepository;
    this.gatewayPushPublisher = gatewayPushPublisher;
    this.properties = properties;
    this.tokenVersionService = tokenVersionService;
  }

  public PushResult pushToUsers(long tenantId, Collection<Long> userIds, int cmd, byte[] body, boolean needAck) {
    return pushToUsers(tenantId, userIds, cmd, body, needAck, 0L, "");
  }

  public PushResult pushToUsers(long tenantId, Collection<Long> userIds, int cmd, byte[] body, boolean needAck,
      long excludeUserId, String excludeConnId) {
    validateTenant(tenantId);
    if (userIds == null || userIds.isEmpty()) {
      return new PushResult(0, 0);
    }
    List<Long> targetUserIds = normalizeUserIds(userIds);
    if (targetUserIds.isEmpty()) {
      return new PushResult(0, 0);
    }
    Map<Long, List<OnlineRoute>> routesByUser = groupRoutesByUser(
        routeRepository.findAllByUsers(tenantId, targetUserIds));
    Map<String, List<OnlineRoute>> routesByGateway = new LinkedHashMap<>();
    int offlineUsers = 0;
    int onlineRoutes = 0;
    for (Long userId : targetUserIds) {
      List<OnlineRoute> routes = routesByUser.getOrDefault(userId, List.of())
          .stream()
          .filter(route -> !isExcluded(route, excludeUserId, excludeConnId))
          .toList();
      if (routes.isEmpty()) {
        offlineUsers++;
        continue;
      }
      onlineRoutes += routes.size();
      for (OnlineRoute route : routes) {
        routesByGateway.computeIfAbsent(route.gwInstance(), ignored -> new ArrayList<>()).add(route);
      }
    }
    publishGrouped(tenantId, cmd, body, needAck, routesByGateway);
    return new PushResult(onlineRoutes, offlineUsers);
  }

  public PushResult kickUser(long tenantId, long userId, int platform, int reason) {
    validateTenant(tenantId);
    PlatformClass platformClass = PlatformClass.fromPlatform(platform);
    tokenVersionService.nextVersion(tenantId, userId, platformClass.key());
    return routeRepository.find(tenantId, userId, platform)
        .map(route -> {
          publishKick(route, reason);
          return new PushResult(1, 0);
        })
        .orElseGet(() -> new PushResult(0, 1));
  }

  public void onConnected(ConnCtx ctx) {
    OnlineRoute newRoute = OnlineRoute.from(ctx);
    routeRepository.find(newRoute.tenantId(), newRoute.userId(), newRoute.platform())
        .filter(oldRoute -> !oldRoute.sameConnection(newRoute))
        .ifPresent(oldRoute -> publishKick(oldRoute, KickNotify.Reason.NEW_DEVICE_LOGIN_VALUE));
    routeRepository.save(newRoute, properties.routeTtl());
  }

  public void onDisconnected(ConnCtx ctx) {
    routeRepository.deleteIfCurrent(OnlineRoute.from(ctx));
  }

  private void publishGrouped(long tenantId, int cmd, byte[] body, boolean needAck,
      Map<String, List<OnlineRoute>> routesByGateway) {
    for (Map.Entry<String, List<OnlineRoute>> entry : routesByGateway.entrySet()) {
      PushEnvelope.Builder envelope = PushEnvelope.newBuilder()
          .setTenantId(tenantId)
          .setCmd(cmd)
          .setBody(ByteString.copyFrom(body == null ? new byte[0] : body))
          .setTraceId(TraceContext.currentOrCreateTraceId())
          .setNeedAck(needAck);
      entry.getValue().forEach(route -> envelope.addTargets(toTarget(route)));
      gatewayPushPublisher.publish(entry.getKey(), envelope.build());
    }
  }

  private List<Long> normalizeUserIds(Collection<Long> userIds) {
    LinkedHashSet<Long> normalized = new LinkedHashSet<>();
    for (Long userId : userIds) {
      if (userId != null && userId > 0) {
        normalized.add(userId);
      }
    }
    return List.copyOf(normalized);
  }

  private Map<Long, List<OnlineRoute>> groupRoutesByUser(List<OnlineRoute> routes) {
    Map<Long, List<OnlineRoute>> grouped = new LinkedHashMap<>();
    for (OnlineRoute route : routes) {
      grouped.computeIfAbsent(route.userId(), ignored -> new ArrayList<>()).add(route);
    }
    return grouped;
  }

  private boolean isExcluded(OnlineRoute route, long excludeUserId, String excludeConnId) {
    if (excludeUserId <= 0 || excludeConnId == null || excludeConnId.isBlank()) {
      return false;
    }
    return route.userId() == excludeUserId && excludeConnId.equals(route.connId());
  }

  private void publishKick(OnlineRoute route, int reason) {
    KickNotify notify = KickNotify.newBuilder()
        .setReasonValue(reason)
        .setMessage(kickMessage(reason))
        .build();
    Map<String, List<OnlineRoute>> grouped = Map.of(route.gwInstance(), List.of(route));
    publishGrouped(route.tenantId(), Cmd.KICK_VALUE, notify.toByteArray(), false, grouped);
  }

  private PushTarget toTarget(OnlineRoute route) {
    return PushTarget.newBuilder()
        .setUserId(route.userId())
        .setPlatform(route.platform())
        .setConnId(route.connId())
        .build();
  }

  private void validateTenant(long tenantId) {
    if (tenantId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "tenant_id must be positive");
    }
  }

  private String kickMessage(int reason) {
    if (reason == KickNotify.Reason.NEW_DEVICE_LOGIN_VALUE) {
      return "new device login";
    }
    if (reason == KickNotify.Reason.ADMIN_OFFLINE_VALUE) {
      return "admin offline";
    }
    if (reason == KickNotify.Reason.TOKEN_EXPIRED_VALUE) {
      return "token expired";
    }
    if (reason == KickNotify.Reason.PROTO_TOO_OLD_VALUE) {
      return "protocol too old";
    }
    return "kicked";
  }
}
