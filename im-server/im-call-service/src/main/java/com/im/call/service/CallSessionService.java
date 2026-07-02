package com.im.call.service;

import com.im.call.config.CallProperties;
import com.im.common.redis.RedisKeys;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * 呼叫状态机（服务端权威，Redis 承载，D45）。
 *
 * <p>状态：INVITING → ACTIVE → 删除（ENDED 无持久驻留态，终态落 call_record）。
 * 所有迁移用 Lua 原子执行——多端同时接听只有一个赢；占线标记与会话键同生共死。
 */
@Service
public class CallSessionService {

  private static final String F_CALLER = "caller_id";
  private static final String F_CALLEE = "callee_id";
  private static final String F_MEDIA = "media";
  private static final String F_STATE = "state";
  private static final String F_CLIENT_CALL_ID = "client_call_id";
  private static final String F_INVITE_AT = "invite_at_ms";
  private static final String F_ANSWER_AT = "answer_at_ms";

  /**
   * 建呼叫：主叫/被叫占线检查 + 会话创建，一次 Lua 原子完成。
   * KEYS[1]=callerBusy KEYS[2]=calleeBusy KEYS[3]=session KEYS[4]=idem KEYS[5]=deadlineZset
   * ARGV: callId, callerId, calleeId, media, clientCallId, nowMs, ringTtlMs, deadlineMember, deadlineScore
   * 返回 1=成功 0=忙线。
   */
  private static final DefaultRedisScript<Long> CREATE = new DefaultRedisScript<>("""
      if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then
        return 0
      end
      redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[7])
      redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[7])
      redis.call('HSET', KEYS[3],
        'caller_id', ARGV[2], 'callee_id', ARGV[3], 'media', ARGV[4],
        'state', 'INVITING', 'client_call_id', ARGV[5],
        'invite_at_ms', ARGV[6], 'answer_at_ms', '0')
      redis.call('PEXPIRE', KEYS[3], ARGV[7])
      redis.call('SET', KEYS[4], ARGV[1], 'PX', ARGV[7])
      redis.call('ZADD', KEYS[5], ARGV[9], ARGV[8])
      return 1
      """, Long.class);

  /**
   * 接听：INVITING→ACTIVE（CAS），并把会话/占线 TTL 延长为通话上限。
   * KEYS[1]=session KEYS[2]=callerBusy KEYS[3]=calleeBusy KEYS[4]=deadlineZset
   * ARGV: nowMs, activeTtlMs, deadlineMember
   * 返回 1=赢得接听 0=状态不对（已接/已结束）。
   */
  private static final DefaultRedisScript<Long> ACCEPT = new DefaultRedisScript<>("""
      if redis.call('HGET', KEYS[1], 'state') ~= 'INVITING' then
        return 0
      end
      redis.call('HSET', KEYS[1], 'state', 'ACTIVE', 'answer_at_ms', ARGV[1])
      redis.call('PEXPIRE', KEYS[1], ARGV[2])
      redis.call('PEXPIRE', KEYS[2], ARGV[2])
      redis.call('PEXPIRE', KEYS[3], ARGV[2])
      redis.call('ZREM', KEYS[4], ARGV[3])
      return 1
      """, Long.class);

  /**
   * 终结：删除会话 + 双方占线 + 幂等键 + 摘 deadline。幂等（键不在也安全）。
   * KEYS[1]=session KEYS[2]=callerBusy KEYS[3]=calleeBusy KEYS[4]=idem KEYS[5]=deadlineZset
   * ARGV: deadlineMember
   * 返回被删的 session 数（1=本次真正终结，0=已被并发终结）。
   */
  private static final DefaultRedisScript<Long> END = new DefaultRedisScript<>("""
      local removed = redis.call('DEL', KEYS[1])
      redis.call('DEL', KEYS[2], KEYS[3], KEYS[4])
      redis.call('ZREM', KEYS[5], ARGV[1])
      return removed
      """, Long.class);

  private final StringRedisTemplate redis;
  private final CallProperties properties;

  public CallSessionService(StringRedisTemplate redis, CallProperties properties) {
    this.redis = redis;
    this.properties = properties;
  }

  /** 幂等查找：同 clientCallId 的重复 INVITE 返回既有会话。 */
  public Optional<CallSession> findByClientCallId(long tenantId, String clientCallId) {
    String callId = redis.opsForValue().get(RedisKeys.callIdempotency(tenantId, clientCallId));
    return callId == null ? Optional.empty() : find(tenantId, callId);
  }

  /** 建呼叫；返回空 = 忙线（主叫或被叫已有振铃/通话）。 */
  public Optional<CallSession> create(
      long tenantId, long callerId, long calleeId, int media, String clientCallId) {
    String callId = UUID.randomUUID().toString();
    long now = System.currentTimeMillis();
    long ringTtlMs = properties.ringTimeout().plusSeconds(30).toMillis(); // TTL 略大于振铃超时兜底
    long deadline = now + properties.ringTimeout().toMillis();
    Long created = redis.execute(
        CREATE,
        List.of(
            RedisKeys.callUserBusy(tenantId, callerId),
            RedisKeys.callUserBusy(tenantId, calleeId),
            RedisKeys.callSession(tenantId, callId),
            RedisKeys.callIdempotency(tenantId, clientCallId),
            RedisKeys.callRingDeadlines()),
        callId,
        String.valueOf(callerId),
        String.valueOf(calleeId),
        String.valueOf(media),
        clientCallId,
        String.valueOf(now),
        String.valueOf(ringTtlMs),
        deadlineMember(tenantId, callId),
        String.valueOf(deadline));
    if (created == null || created != 1L) {
      return Optional.empty();
    }
    return Optional.of(new CallSession(
        callId, callerId, calleeId, media, CallSession.STATE_INVITING, clientCallId, now, 0L));
  }

  public Optional<CallSession> find(long tenantId, String callId) {
    Map<Object, Object> hash = redis.opsForHash().entries(RedisKeys.callSession(tenantId, callId));
    if (hash == null || hash.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new CallSession(
        callId,
        parseLong(hash.get(F_CALLER)),
        parseLong(hash.get(F_CALLEE)),
        (int) parseLong(hash.get(F_MEDIA)),
        String.valueOf(hash.getOrDefault(F_STATE, "")),
        String.valueOf(hash.getOrDefault(F_CLIENT_CALL_ID, "")),
        parseLong(hash.get(F_INVITE_AT)),
        parseLong(hash.get(F_ANSWER_AT))));
  }

  /** 接听 CAS；true=本端赢得接听。 */
  public boolean accept(long tenantId, CallSession session) {
    Long won = redis.execute(
        ACCEPT,
        List.of(
            RedisKeys.callSession(tenantId, session.callId()),
            RedisKeys.callUserBusy(tenantId, session.callerUserId()),
            RedisKeys.callUserBusy(tenantId, session.calleeUserId()),
            RedisKeys.callRingDeadlines()),
        String.valueOf(System.currentTimeMillis()),
        String.valueOf(properties.activeTtl().toMillis()),
        deadlineMember(tenantId, session.callId()));
    return won != null && won == 1L;
  }

  /** 终结并清理；true=本次调用真正终结（并发终结只有一个 true，据此写 CDR 恰好一次）。 */
  public boolean end(long tenantId, CallSession session) {
    Long removed = redis.execute(
        END,
        List.of(
            RedisKeys.callSession(tenantId, session.callId()),
            RedisKeys.callUserBusy(tenantId, session.callerUserId()),
            RedisKeys.callUserBusy(tenantId, session.calleeUserId()),
            RedisKeys.callIdempotency(tenantId, session.clientCallId()),
            RedisKeys.callRingDeadlines()),
        deadlineMember(tenantId, session.callId()));
    return removed != null && removed == 1L;
  }

  /** 取到期振铃（sweeper 用）：ZRANGEBYSCORE + 原子 ZREM，摘到才拥有处理权。 */
  public List<String> claimExpiredRings(long nowMs, int limit) {
    var members = redis.opsForZSet()
        .rangeByScore(RedisKeys.callRingDeadlines(), 0, nowMs, 0, limit);
    if (members == null || members.isEmpty()) {
      return List.of();
    }
    return members.stream()
        .filter(member -> {
          Long removed = redis.opsForZSet().remove(RedisKeys.callRingDeadlines(), member);
          return removed != null && removed > 0; // 多实例竞争：摘到者处理
        })
        .toList();
  }

  public static String deadlineMember(long tenantId, String callId) {
    return tenantId + ":" + callId;
  }

  private static long parseLong(Object value) {
    if (value == null) {
      return 0L;
    }
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException ex) {
      return 0L;
    }
  }
}
