package com.im.push.route;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OnlineRouteRepository {

  void save(OnlineRoute route, Duration ttl);

  /**
   * 原子写入新路由并返回被它顶替的旧路由（若有）。
   *
   * <p>用于 onConnected：set 总是生效（新连接赢得路由键），同时拿到被顶替的旧路由用于踢线。
   * 相比"先 find 再 save"，消除了同平台并发登录时两端都读到空 → 后写者静默覆盖前写者、
   * 使前一条连接成为"在线但收不到推送"孤儿路由的竞态（参见架构审查 P2-2）。
   */
  Optional<OnlineRoute> saveReturningPrevious(OnlineRoute route, Duration ttl);

  boolean refreshIfCurrent(OnlineRoute route, Duration ttl);

  Optional<OnlineRoute> find(long tenantId, long userId, int platform);

  List<OnlineRoute> findAll(long tenantId, long userId);

  List<OnlineRoute> findAllByUsers(long tenantId, Collection<Long> userIds);

  boolean deleteIfCurrent(OnlineRoute route);
}
