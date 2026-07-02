package com.im.call.service;

import com.im.common.tenant.TenantContext;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

/**
 * 振铃超时 sweeper（D45）：每秒扫 Redis deadline ZSET，到期未接的呼叫代答 TIMEOUT。
 *
 * <p>与 OutboxPoller 同构（SmartLifecycle + 虚拟线程循环，不引入 @Scheduled）。
 * 多实例安全：claimExpiredRings 内 ZREM 原子摘除，摘到者才拥有处理权。
 */
@Service
public class CallTimeoutSweeper implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(CallTimeoutSweeper.class);
  private static final long SWEEP_INTERVAL_MS = 1000;
  private static final int BATCH_LIMIT = 32;

  private final CallSessionService sessions;
  private final CallService callService;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private ExecutorService executor;

  public CallTimeoutSweeper(CallSessionService sessions, CallService callService) {
    this.sessions = sessions;
    this.callService = callService;
  }

  @Override
  public void start() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    executor = Executors.newVirtualThreadPerTaskExecutor();
    executor.submit(this::sweepLoop);
    log.info("call timeout sweeper started");
  }

  @Override
  public void stop() {
    if (!running.compareAndSet(true, false)) {
      return;
    }
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  @Override
  public boolean isAutoStartup() {
    return true;
  }

  private void sweepLoop() {
    while (running.get()) {
      try {
        sweepOnce();
        TimeUnit.MILLISECONDS.sleep(SWEEP_INTERVAL_MS);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        running.set(false);
      } catch (Exception ex) {
        log.warn("call timeout sweep failed", ex);
      }
    }
  }

  void sweepOnce() {
    List<String> claimed = sessions.claimExpiredRings(System.currentTimeMillis(), BATCH_LIMIT);
    for (String member : claimed) {
      // member = "{tenantId}:{callId}"（callId 为 UUID，含 '-' 无 ':'，首个冒号即分隔位）
      int sep = member.indexOf(':');
      if (sep <= 0) {
        log.warn("skip malformed call deadline member: {}", member);
        continue;
      }
      long tenantId;
      try {
        tenantId = Long.parseLong(member.substring(0, sep));
      } catch (NumberFormatException ex) {
        log.warn("skip malformed call deadline member: {}", member);
        continue;
      }
      String callId = member.substring(sep + 1);
      TenantContext.runWithTenant(tenantId, () -> callService.timeoutRing(tenantId, callId));
    }
  }
}
