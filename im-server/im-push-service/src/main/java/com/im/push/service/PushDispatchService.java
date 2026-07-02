package com.im.push.service;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PushDispatchService {

  private static final Logger log = LoggerFactory.getLogger(PushDispatchService.class);

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
    // P3：每条推送都打日志在高吞吐下量很大，降到 debug。
    log.debug("push dispatch resolved, tenant_id={}, cmd={}, target_users={}, online_routes={}, offline_users={}, gateways={}",
        tenantId, cmd, targetUserIds.size(), onlineRoutes, offlineUsers, routesByGateway.keySet());
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
    // 原子写入新路由并拿回被顶替的旧路由：set 必然生效（新连接赢得路由键），
    // 旧路由若是不同连接则踢线。避免"先 find 再 save"在同平台并发登录下的孤儿路由竞态（P2-2）。
    routeRepository.saveReturningPrevious(newRoute, properties.routeTtl())
        .filter(oldRoute -> !oldRoute.sameConnection(newRoute))
        .ifPresent(oldRoute -> publishKick(oldRoute, KickNotify.Reason.NEW_DEVICE_LOGIN_VALUE));
  }

  public void refreshRoute(ConnCtx ctx) {
    routeRepository.refreshIfCurrent(OnlineRoute.from(ctx), properties.routeTtl());
  }

  public void onDisconnected(ConnCtx ctx) {
    routeRepository.deleteIfCurrent(OnlineRoute.from(ctx));
  }

  private void publishGrouped(long tenantId, int cmd, byte[] body, boolean needAck,
      Map<String, List<OnlineRoute>> routesByGateway) {
    // J1（2026-07-02 结构审查）：body 来自事件解码后只读，不再有写入方，
    // unsafeWrap 免掉每个网关分组一次的深拷贝（copyFrom）。
    ByteString bodyBytes = body == null || body.length == 0
        ? ByteString.EMPTY
        : UnsafeByteOperations.unsafeWrap(body);
    for (Map.Entry<String, List<OnlineRoute>> entry : routesByGateway.entrySet()) {
      PushEnvelope.Builder envelope = PushEnvelope.newBuilder()
          .setTenantId(tenantId)
          .setCmd(cmd)
          .setBody(bodyBytes)
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
    KickNotify.Reason r = KickNotify.Reason.forNumber(reason);
    if (r == null) {
      return "kicked";
    }
    return switch (r) {
      case NEW_DEVICE_LOGIN -> "new device login";
      case ADMIN_OFFLINE -> "admin offline";
      case TOKEN_EXPIRED -> "token expired";
      case PROTO_TOO_OLD -> "protocol too old";
      default -> "kicked";
    };
  }
}
