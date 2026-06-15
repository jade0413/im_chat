package com.im.user.friend;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.id.SnowflakeIdGenerator;
import com.im.common.tenant.TenantContext;
import com.im.user.dao.entity.FriendEntity;
import com.im.user.dao.entity.FriendRequestEntity;
import com.im.user.dao.entity.UserEntity;
import com.im.user.dao.mapper.FriendMapper;
import com.im.user.dao.mapper.FriendRequestMapper;
import com.im.user.dao.mapper.UserMapper;
import com.im.user.friend.dto.FriendItemResponse;
import com.im.user.friend.dto.FriendRequestItemResponse;
import com.im.user.friend.dto.SendFriendRequestResponse;
import com.im.user.service.RelationService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 好友申请 / 关系（D40）。状态唯一真相在 friend_request；通知经 {@link FriendNotificationPort}
 * 写接收方 SYSTEM 会话，只带 request_id。详见 docs/friend-service-design.md。
 */
@Service
public class FriendService {

  private static final int USER_TYPE_MEMBER = 1;
  private static final int STATUS_BANNED = 3;
  private static final int VERIFY_REQUIRED_DEFAULT = 1;

  private final FriendRequestMapper friendRequestMapper;
  private final FriendMapper friendMapper;
  private final UserMapper userMapper;
  private final RelationService relationService;
  private final SnowflakeIdGenerator idGenerator;
  private final FriendNotificationPort notificationPort;
  private final ObjectMapper objectMapper;

  public FriendService(FriendRequestMapper friendRequestMapper, FriendMapper friendMapper,
      UserMapper userMapper, RelationService relationService, SnowflakeIdGenerator idGenerator,
      FriendNotificationPort notificationPort, ObjectMapper objectMapper) {
    this.friendRequestMapper = friendRequestMapper;
    this.friendMapper = friendMapper;
    this.userMapper = userMapper;
    this.relationService = relationService;
    this.idGenerator = idGenerator;
    this.notificationPort = notificationPort;
    this.objectMapper = objectMapper;
  }

  // ---------------- 发起申请 ----------------

  @Transactional
  public SendFriendRequestResponse sendRequest(long fromUserId, long toUserId, String rawNote) {
    long tenantId = TenantContext.requiredTenantId();
    if (fromUserId == toUserId) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "不能添加自己为好友");
    }
    String note = rawNote == null ? "" : rawNote.trim();

    // 发起方必须是正常 member（D40：visitor/agent 不进好友体系，封禁用户不可发起）
    UserEntity sender = userMapper.selectById(fromUserId);
    if (sender == null || isBanned(sender) || !isMember(sender)) {
      throw new ImException(ErrorCode.NO_PERMISSION, "当前账号不可发起好友申请");
    }

    UserEntity target = userMapper.selectById(toUserId);
    if (target == null || isBanned(target) || !isMember(target)) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "目标用户不存在或不可添加");
    }

    // 黑名单：对方拉黑我 → 静默成功（不建 row、不通知，不泄露拉黑事实，D40）
    if (relationService.check(fromUserId, toUserId).blockedByPeer()) {
      return SendFriendRequestResponse.ok();
    }
    // 已是好友
    if (isFriend(fromUserId, toUserId)) {
      return SendFriendRequestResponse.alreadyFriend();
    }
    // 已有 pending → 更新备注并重发通知（幂等，不新建）
    FriendRequestEntity pending = findPending(fromUserId, toUserId);
    if (pending != null) {
      pending.setNote(note);
      friendRequestMapper.updateById(pending);
      notifyFriendRequest(tenantId, fromUserId, pending);
      return SendFriendRequestResponse.pending(pending.getId());
    }

    boolean autoAccept = verifyRequired(target) == 0;
    FriendRequestEntity req = new FriendRequestEntity();
    req.setId(idGenerator.nextId());
    req.setTenantId(tenantId);
    req.setFromUserId(fromUserId);
    req.setToUserId(toUserId);
    req.setNote(note);
    req.setAutoAccepted(autoAccept ? 1 : 0);

    if (autoAccept) {
      req.setStatus(FriendRequestEntity.STATUS_ACCEPTED);
      req.setHandleTime(LocalDateTime.now());
      friendRequestMapper.insert(req);
      linkFriends(tenantId, fromUserId, toUserId);
      // 对方免验证，被直接加：通知接收方（历史可见）
      notify(tenantId, toUserId, FriendNotificationPort.EVENT_FRIEND_ADDED,
          addedPayload(req, fromUserId));
      return SendFriendRequestResponse.accepted(req.getId());
    }

    req.setStatus(FriendRequestEntity.STATUS_PENDING);
    try {
      friendRequestMapper.insert(req);
    } catch (DuplicateKeyException ex) {
      // 并发下另一线程已抢先建 pending（uk_pending 命中）：按既有 pending 幂等返回
      FriendRequestEntity raced = findPending(fromUserId, toUserId);
      if (raced == null) {
        throw new ImException(ErrorCode.INTERNAL_ERROR, "pending conflict but row not found", ex);
      }
      raced.setNote(note);
      friendRequestMapper.updateById(raced);
      notifyFriendRequest(tenantId, fromUserId, raced);
      return SendFriendRequestResponse.pending(raced.getId());
    }
    notifyFriendRequest(tenantId, fromUserId, req);
    return SendFriendRequestResponse.pending(req.getId());
  }

  // ---------------- 处理申请 ----------------

  @Transactional
  public void accept(long requestId, long currentUserId) {
    long tenantId = TenantContext.requiredTenantId();
    FriendRequestEntity req = loadHandlable(requestId, currentUserId);
    if (req.getStatus() == FriendRequestEntity.STATUS_ACCEPTED) {
      return; // 幂等
    }
    int rows = transitionPending(requestId, FriendRequestEntity.STATUS_ACCEPTED, true);
    if (rows == 0) {
      return; // 已被其它端处理，幂等返回
    }
    linkFriends(tenantId, req.getFromUserId(), req.getToUserId());
    // 通知申请方：已通过，可开聊
    notify(tenantId, req.getFromUserId(), FriendNotificationPort.EVENT_FRIEND_ACCEPTED,
        acceptedPayload(req));
  }

  /** 拒绝：不通知申请方（D40），可再次申请。 */
  @Transactional
  public void reject(long requestId, long currentUserId) {
    loadHandlable(requestId, currentUserId);
    transitionPending(requestId, FriendRequestEntity.STATUS_REJECTED, true);
  }

  /** 忽略：不通知申请方，可再次申请。 */
  @Transactional
  public void ignore(long requestId, long currentUserId) {
    loadHandlable(requestId, currentUserId);
    transitionPending(requestId, FriendRequestEntity.STATUS_IGNORED, true);
  }

  // ---------------- 查询 ----------------

  /** role=incoming 收到的；outgoing 我发出的。倒序，最多 50 条。 */
  public List<FriendRequestItemResponse> listRequests(long userId, String role, int limit) {
    TenantContext.requiredTenantId();
    boolean incoming = !"outgoing".equalsIgnoreCase(role);
    int safeLimit = limit <= 0 || limit > 100 ? 50 : limit;
    var query = Wrappers.lambdaQuery(FriendRequestEntity.class);
    if (incoming) {
      query.eq(FriendRequestEntity::getToUserId, userId);
    } else {
      query.eq(FriendRequestEntity::getFromUserId, userId);
    }
    query.orderByDesc(FriendRequestEntity::getCreateTime).last("LIMIT " + safeLimit);
    List<FriendRequestEntity> rows = friendRequestMapper.selectList(query);
    Map<Long, UserEntity> peers = loadUsers(rows.stream()
        .map(r -> incoming ? r.getFromUserId() : r.getToUserId())
        .collect(Collectors.toList()));
    return rows.stream().map(r -> {
      long peerId = incoming ? r.getFromUserId() : r.getToUserId();
      UserEntity p = peers.get(peerId);
      return new FriendRequestItemResponse(
          r.getId(), r.getFromUserId(), r.getToUserId(), r.getNote(),
          r.getStatus(), Integer.valueOf(1).equals(r.getAutoAccepted()),
          epochMilli(r.getCreateTime()), peerId,
          p == null ? "" : p.getNickname(), p == null ? "" : p.getAvatar(),
          p == null ? null : p.getUsername());
    }).toList();
  }

  public List<FriendItemResponse> listFriends(long userId) {
    TenantContext.requiredTenantId();
    List<FriendEntity> rows = friendMapper.selectList(
        Wrappers.lambdaQuery(FriendEntity.class)
            .eq(FriendEntity::getUserId, userId)
            .orderByDesc(FriendEntity::getCreatedAt));
    Map<Long, UserEntity> friends = loadUsers(
        rows.stream().map(FriendEntity::getFriendUserId).collect(Collectors.toList()));
    return rows.stream().map(f -> {
      UserEntity u = friends.get(f.getFriendUserId());
      return new FriendItemResponse(
          f.getFriendUserId(), f.getRemark(),
          u == null ? "" : u.getNickname(), u == null ? "" : u.getAvatar(),
          u == null ? null : u.getUsername());
    }).toList();
  }

  // ---------------- 关系维护 / 设置 ----------------

  @Transactional
  public void deleteFriend(long userId, long friendUserId) {
    TenantContext.requiredTenantId();
    friendMapper.delete(Wrappers.lambdaQuery(FriendEntity.class)
        .eq(FriendEntity::getUserId, userId)
        .eq(FriendEntity::getFriendUserId, friendUserId));
    friendMapper.delete(Wrappers.lambdaQuery(FriendEntity.class)
        .eq(FriendEntity::getUserId, friendUserId)
        .eq(FriendEntity::getFriendUserId, userId));
  }

  @Transactional
  public void updateRemark(long userId, long friendUserId, String remark) {
    TenantContext.requiredTenantId();
    int rows = friendMapper.update(null, Wrappers.lambdaUpdate(FriendEntity.class)
        .set(FriendEntity::getRemark, remark == null ? "" : remark)
        .eq(FriendEntity::getUserId, userId)
        .eq(FriendEntity::getFriendUserId, friendUserId));
    if (rows == 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "不是好友关系");
    }
  }

  @Transactional
  public void updateVerifySetting(long userId, int friendVerifyRequired) {
    TenantContext.requiredTenantId();
    UserEntity update = new UserEntity();
    update.setId(userId);
    update.setFriendVerifyRequired(friendVerifyRequired);
    userMapper.updateById(update);
  }

  // ---------------- 内部辅助 ----------------

  private FriendRequestEntity loadHandlable(long requestId, long currentUserId) {
    FriendRequestEntity req = friendRequestMapper.selectById(requestId);
    if (req == null || req.getToUserId() != currentUserId) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "申请不存在");
    }
    return req;
  }

  /** 条件流转：仅 pending → 目标态，并发安全。返回受影响行数。 */
  private int transitionPending(long requestId, int targetStatus, boolean writeHandleTime) {
    return friendRequestMapper.update(null, Wrappers.lambdaUpdate(FriendRequestEntity.class)
        .set(FriendRequestEntity::getStatus, targetStatus)
        .set(writeHandleTime, FriendRequestEntity::getHandleTime, LocalDateTime.now())
        .eq(FriendRequestEntity::getId, requestId)
        .eq(FriendRequestEntity::getStatus, FriendRequestEntity.STATUS_PENDING));
  }

  /** 建立双向好友行（幂等：已存在则跳过）。 */
  private void linkFriends(long tenantId, long a, long b) {
    insertFriendRow(tenantId, a, b);
    insertFriendRow(tenantId, b, a);
  }

  private void insertFriendRow(long tenantId, long owner, long friend) {
    boolean exists = friendMapper.selectCount(Wrappers.lambdaQuery(FriendEntity.class)
        .eq(FriendEntity::getUserId, owner)
        .eq(FriendEntity::getFriendUserId, friend)) > 0;
    if (exists) {
      return;
    }
    FriendEntity row = new FriendEntity();
    row.setTenantId(tenantId);
    row.setUserId(owner);
    row.setFriendUserId(friend);
    row.setRemark("");
    row.setStatus(FriendEntity.STATUS_NORMAL);
    friendMapper.insert(row);
  }

  private boolean isFriend(long userId, long friendUserId) {
    return friendMapper.selectCount(Wrappers.lambdaQuery(FriendEntity.class)
        .eq(FriendEntity::getUserId, userId)
        .eq(FriendEntity::getFriendUserId, friendUserId)) > 0;
  }

  private FriendRequestEntity findPending(long fromUserId, long toUserId) {
    return friendRequestMapper.selectOne(Wrappers.lambdaQuery(FriendRequestEntity.class)
        .eq(FriendRequestEntity::getFromUserId, fromUserId)
        .eq(FriendRequestEntity::getToUserId, toUserId)
        .eq(FriendRequestEntity::getStatus, FriendRequestEntity.STATUS_PENDING)
        .last("LIMIT 1"));
  }

  private Map<Long, UserEntity> loadUsers(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }
    long tenantId = TenantContext.requiredTenantId();
    return userMapper.findByIds(tenantId, ids).stream()
        .collect(Collectors.toMap(UserEntity::getId, u -> u, (x, y) -> x));
  }

  private void notifyFriendRequest(long tenantId, long fromUserId, FriendRequestEntity req) {
    notify(tenantId, req.getToUserId(), FriendNotificationPort.EVENT_FRIEND_REQUEST,
        requestPayload(req, fromUserId));
  }

  private void notify(long tenantId, long toUserId, String eventType, String payload) {
    notificationPort.send(tenantId, toUserId, eventType, payload);
  }

  private String requestPayload(FriendRequestEntity req, long fromUserId) {
    UserEntity from = userMapper.selectById(fromUserId);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("request_id", req.getId());
    m.put("from_user_id", fromUserId);
    m.put("from_nickname", from == null ? "" : from.getNickname());
    m.put("from_avatar", from == null ? "" : from.getAvatar());
    m.put("from_username", from == null ? null : from.getUsername());
    m.put("note", req.getNote());
    m.put("time", epochMilli(req.getCreateTime()));
    return toJson(m);
  }

  private String addedPayload(FriendRequestEntity req, long fromUserId) {
    UserEntity from = userMapper.selectById(fromUserId);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("request_id", req.getId());
    m.put("from_user_id", fromUserId);
    m.put("from_nickname", from == null ? "" : from.getNickname());
    m.put("note", req.getNote());
    m.put("time", epochMilli(req.getHandleTime()));
    return toJson(m);
  }

  private String acceptedPayload(FriendRequestEntity req) {
    UserEntity to = userMapper.selectById(req.getToUserId());
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("request_id", req.getId());
    m.put("to_user_id", req.getToUserId());
    m.put("to_nickname", to == null ? "" : to.getNickname());
    m.put("time", System.currentTimeMillis());
    return toJson(m);
  }

  private String toJson(Map<String, Object> m) {
    try {
      return objectMapper.writeValueAsString(m);
    } catch (JsonProcessingException e) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "序列化通知失败");
    }
  }

  private static long epochMilli(LocalDateTime t) {
    return t == null ? 0L : t.toInstant(ZoneOffset.UTC).toEpochMilli();
  }

  private boolean isMember(UserEntity u) {
    return u.getUserType() != null && u.getUserType() == USER_TYPE_MEMBER;
  }

  private boolean isBanned(UserEntity u) {
    return u.getStatus() != null && u.getStatus() == STATUS_BANNED;
  }

  private int verifyRequired(UserEntity u) {
    return u.getFriendVerifyRequired() == null ? VERIFY_REQUIRED_DEFAULT : u.getFriendVerifyRequired();
  }
}
