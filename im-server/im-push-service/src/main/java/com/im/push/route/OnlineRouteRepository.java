package com.im.push.route;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface OnlineRouteRepository {

  void save(OnlineRoute route, Duration ttl);

  Optional<OnlineRoute> find(long tenantId, long userId, int platform);

  List<OnlineRoute> findAll(long tenantId, long userId);

  boolean deleteIfCurrent(OnlineRoute route);
}
