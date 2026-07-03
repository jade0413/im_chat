package com.im.call.service;

import com.im.call.config.CallProperties;
import com.im.common.redis.RedisKeys;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  private static final String F_GROUP_ID = "group_id";
  private static final String F_INVITED_IDS = "invited_ids";
  private static final String F_ACTIVE_IDS = "active_ids";
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
        'group_id', '0', 'invited_ids', '', 'active_ids', '',
        'state', 'INVITING', 'client_call_id', ARGV[5],
        'invite_at_ms', ARGV[6], 'answer_at_ms', '0')
      redis.call('PEXPIRE', KEYS[3], ARGV[7])
      redis.call('SET', KEYS[4], ARGV[1], 'PX', ARGV[7])
      redis.call('ZADD', KEYS[5], ARGV[9], ARGV[8])
      return 1
      """, Long.class);

  /**
   * 接听：1v1 是 INVITING→ACTIVE（CAS）；群通话允许候选成员从 INVITING/ACTIVE 陆续加入。
   * KEYS[1]=session KEYS[2]=callerBusy KEYS[3]=calleeBusy/answererBusy KEYS[4]=deadlineZset
   * ARGV: nowMs, activeTtlMs, deadlineMember, callId, answererId
   * 返回 1=接听成功 0=状态不对/忙线/非候选成员。
   */
  private static final DefaultRedisScript<Long> ACCEPT = new DefaultRedisScript<>("""
      local function contains(csv, id)
        if not csv or csv == '' then return false end
        return string.find(',' .. csv .. ',', ',' .. id .. ',', 1, true) ~= nil
      end
      local function append(csv, id)
        if not csv or csv == '' then return id end
        if contains(csv, id) then return csv end
        return csv .. ',' .. id
      end
      local state = redis.call('HGET', KEYS[1], 'state')
      local group_id = redis.call('HGET', KEYS[1], 'group_id')
      local answerer = ARGV[5]
      if group_id and group_id ~= '0' then
        if state ~= 'INVITING' and state ~= 'ACTIVE' then
          return 0
        end
        if not contains(redis.call('HGET', KEYS[1], 'invited_ids'), answerer) then
          return 0
        end
        local busy = redis.call('GET', KEYS[3])
        if busy and busy ~= ARGV[4] then
          return 0
        end
        local active_ids = append(redis.call('HGET', KEYS[1], 'active_ids'), answerer)
        if state == 'INVITING' then
          redis.call('HSET', KEYS[1],
            'state', 'ACTIVE', 'answer_at_ms', ARGV[1], 'callee_id', answerer,
            'active_ids', active_ids)
          redis.call('ZREM', KEYS[4], ARGV[3])
        else
          redis.call('HSET', KEYS[1], 'active_ids', active_ids)
        end
        redis.call('PEXPIRE', KEYS[1], ARGV[2])
        redis.call('PEXPIRE', KEYS[2], ARGV[2])
        redis.call('SET', KEYS[3], ARGV[4], 'PX', ARGV[2])
        return 1
      end
      if state ~= 'INVITING' then
        return 0
      end
      local busy = redis.call('GET', KEYS[3])
      if busy and busy ~= ARGV[4] then
        return 0
      end
      if redis.call('HGET', KEYS[1], 'callee_id') == '0' then
        redis.call('HSET', KEYS[1], 'callee_id', ARGV[5])
      end
      redis.call('HSET', KEYS[1], 'state', 'ACTIVE', 'answer_at_ms', ARGV[1])
      redis.call('PEXPIRE', KEYS[1], ARGV[2])
      redis.call('PEXPIRE', KEYS[2], ARGV[2])
      redis.call('SET', KEYS[3], ARGV[4], 'PX', ARGV[2])
      redis.call('ZREM', KEYS[4], ARGV[3])
      return 1
      """, Long.class);

  /**
   * 建群呼叫：只检查/占用主叫忙线；群成员在真正接听时占线，避免一次邀请锁住整个群。
   * KEYS[1]=callerBusy KEYS[2]=session KEYS[3]=idem KEYS[4]=deadlineZset
   * ARGV: callId, callerId, groupId, invitedIdsCsv, media, clientCallId, nowMs, ringTtlMs,
   * deadlineMember, deadlineScore
   * 返回 1=成功 0=主叫忙线。
   */
  private static final DefaultRedisScript<Long> CREATE_GROUP = new DefaultRedisScript<>("""
      if redis.call('EXISTS', KEYS[1]) == 1 then
        return 0
      end
      redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[8])
      redis.call('HSET', KEYS[2],
        'caller_id', ARGV[2], 'callee_id', '0', 'media', ARGV[5],
        'group_id', ARGV[3], 'invited_ids', ARGV[4], 'active_ids', '',
        'state', 'INVITING', 'client_call_id', ARGV[6],
        'invite_at_ms', ARGV[7], 'answer_at_ms', '0')
      redis.call('PEXPIRE', KEYS[2], ARGV[8])
      redis.call('SET', KEYS[3], ARGV[1], 'PX', ARGV[8])
      redis.call('ZADD', KEYS[4], ARGV[10], ARGV[9])
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

  /** 终结尚未产生实际 callee 的群呼叫。 */
  private static final DefaultRedisScript<Long> END_CALLER_ONLY = new DefaultRedisScript<>("""
      local removed = redis.call('DEL', KEYS[1])
      redis.call('DEL', KEYS[2], KEYS[3])
      redis.call('ZREM', KEYS[4], ARGV[1])
      return removed
      """, Long.class);

  /** 终结群呼叫：删除会话、幂等键、deadline，并清理所有已占用成员的 busy key。 */
  private static final DefaultRedisScript<Long> END_GROUP = new DefaultRedisScript<>("""
      local removed = redis.call('DEL', KEYS[1])
      redis.call('DEL', KEYS[2])
      redis.call('ZREM', KEYS[3], ARGV[1])
      for i = 4, #KEYS do
        redis.call('DEL', KEYS[i])
      end
      return removed
      """, Long.class);

  /**
   * 群成员退出：只移除当前成员 active 标记和 busy key。返回 2=退出后只剩主叫，1=仍有其他成员，0=未在通话中。
   */
  private static final DefaultRedisScript<Long> LEAVE_GROUP = new DefaultRedisScript<>("""
      local function contains(csv, id)
        if not csv or csv == '' then return false end
        return string.find(',' .. csv .. ',', ',' .. id .. ',', 1, true) ~= nil
      end
      local function remove_id(csv, id)
        if not csv or csv == '' then return '' end
        local next = {}
        for part in string.gmatch(csv, '([^,]+)') do
          if part ~= id and part ~= '' then
            table.insert(next, part)
          end
        end
        return table.concat(next, ',')
      end
      if redis.call('HGET', KEYS[1], 'state') ~= 'ACTIVE'
          or redis.call('HGET', KEYS[1], 'group_id') == '0' then
        return 0
      end
      local active_ids = redis.call('HGET', KEYS[1], 'active_ids') or ''
      if not contains(active_ids, ARGV[1]) then
        return 0
      end
      local next_ids = remove_id(active_ids, ARGV[1])
      redis.call('HSET', KEYS[1], 'active_ids', next_ids)
      redis.call('DEL', KEYS[2])
      if next_ids == '' then
        return 2
      end
      return 1
      """, Long.class);

  public enum GroupLeaveResult {
    NOT_ACTIVE,
    LEFT,
    LAST_ACTIVE
  }

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
        callId, callerId, calleeId, 0L, List.of(), List.of(), media,
        CallSession.STATE_INVITING, clientCallId, now, 0L));
  }

  /** 建群呼叫；返回空 = 主叫忙线。 */
  public Optional<CallSession> createGroup(
      long tenantId, long callerId, long groupId, List<Long> invitedUserIds,
      int media, String clientCallId) {
    List<Long> invited = normalizeIds(invitedUserIds, callerId);
    if (groupId <= 0 || invited.isEmpty()) {
      return Optional.empty();
    }
    String callId = UUID.randomUUID().toString();
    long now = System.currentTimeMillis();
    long ringTtlMs = properties.ringTimeout().plusSeconds(30).toMillis();
    long deadline = now + properties.ringTimeout().toMillis();
    Long created = redis.execute(
        CREATE_GROUP,
        List.of(
            RedisKeys.callUserBusy(tenantId, callerId),
            RedisKeys.callSession(tenantId, callId),
            RedisKeys.callIdempotency(tenantId, clientCallId),
            RedisKeys.callRingDeadlines()),
        callId,
        String.valueOf(callerId),
        String.valueOf(groupId),
        joinIds(invited),
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
        callId, callerId, 0L, groupId, invited, List.of(), media,
        CallSession.STATE_INVITING, clientCallId, now, 0L));
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
        parseLong(hash.get(F_GROUP_ID)),
        parseIds(hash.get(F_INVITED_IDS)),
        parseIds(hash.get(F_ACTIVE_IDS)),
        (int) parseLong(hash.get(F_MEDIA)),
        String.valueOf(hash.getOrDefault(F_STATE, "")),
        String.valueOf(hash.getOrDefault(F_CLIENT_CALL_ID, "")),
        parseLong(hash.get(F_INVITE_AT)),
        parseLong(hash.get(F_ANSWER_AT))));
  }

  /** 接听 CAS；true=本端赢得接听。 */
  public Optional<CallSession> accept(long tenantId, CallSession session, long answererUserId) {
    long now = System.currentTimeMillis();
    Long won = redis.execute(
        ACCEPT,
        List.of(
            RedisKeys.callSession(tenantId, session.callId()),
            RedisKeys.callUserBusy(tenantId, session.callerUserId()),
            RedisKeys.callUserBusy(tenantId, answererUserId),
            RedisKeys.callRingDeadlines()),
        String.valueOf(now),
        String.valueOf(properties.activeTtl().toMillis()),
        deadlineMember(tenantId, session.callId()),
        session.callId(),
        String.valueOf(answererUserId));
    if (won == null || won != 1L) {
      return Optional.empty();
    }
    return Optional.of(session.withAcceptedCallee(answererUserId, now));
  }

  /** 终结并清理；true=本次调用真正终结（并发终结只有一个 true，据此写 CDR 恰好一次）。 */
  public boolean end(long tenantId, CallSession session) {
    if (session.groupCall()) {
      List<String> keys = new java.util.ArrayList<>();
      keys.add(RedisKeys.callSession(tenantId, session.callId()));
      keys.add(RedisKeys.callIdempotency(tenantId, session.clientCallId()));
      keys.add(RedisKeys.callRingDeadlines());
      keys.add(RedisKeys.callUserBusy(tenantId, session.callerUserId()));
      session.activeUserIds().stream()
          .filter(id -> id > 0)
          .distinct()
          .map(id -> RedisKeys.callUserBusy(tenantId, id))
          .forEach(keys::add);
      Long removed = redis.execute(
          END_GROUP,
          keys,
          deadlineMember(tenantId, session.callId()));
      return removed != null && removed == 1L;
    }
    if (session.calleeUserId() <= 0) {
      Long removed = redis.execute(
          END_CALLER_ONLY,
          List.of(
              RedisKeys.callSession(tenantId, session.callId()),
              RedisKeys.callUserBusy(tenantId, session.callerUserId()),
              RedisKeys.callIdempotency(tenantId, session.clientCallId()),
              RedisKeys.callRingDeadlines()),
          deadlineMember(tenantId, session.callId()));
      return removed != null && removed == 1L;
    }
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

  public GroupLeaveResult leaveGroup(long tenantId, CallSession session, long userId) {
    if (!session.groupCall() || userId == session.callerUserId()) {
      return GroupLeaveResult.NOT_ACTIVE;
    }
    Long left = redis.execute(
        LEAVE_GROUP,
        List.of(
            RedisKeys.callSession(tenantId, session.callId()),
            RedisKeys.callUserBusy(tenantId, userId)),
        String.valueOf(userId));
    if (left == null || left == 0L) {
      return GroupLeaveResult.NOT_ACTIVE;
    }
    return left == 2L ? GroupLeaveResult.LAST_ACTIVE : GroupLeaveResult.LEFT;
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

  private static List<Long> normalizeIds(List<Long> ids, long excludeUserId) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    Set<Long> normalized = new LinkedHashSet<>();
    for (Long id : ids) {
      if (id != null && id > 0 && id != excludeUserId) {
        normalized.add(id);
      }
    }
    return List.copyOf(normalized);
  }

  private static String joinIds(List<Long> ids) {
    return ids.stream()
        .map(String::valueOf)
        .reduce((left, right) -> left + "," + right)
        .orElse("");
  }

  private static List<Long> parseIds(Object value) {
    if (value == null || String.valueOf(value).isBlank()) {
      return List.of();
    }
    return Arrays.stream(String.valueOf(value).split(","))
        .map(CallSessionService::parseLong)
        .filter(id -> id > 0)
        .toList();
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
