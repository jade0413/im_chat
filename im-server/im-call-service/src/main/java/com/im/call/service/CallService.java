package com.im.call.service;

import com.im.call.config.CallProperties;
import com.im.call.dao.entity.CallRecordEntity;
import com.im.call.dao.mapper.CallRecordMapper;
import com.im.call.service.CallConversationClient.GroupCallTarget;
import com.im.call.service.CallSessionService.GroupLeaveResult;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.proto.body.CallAck;
import com.im.proto.body.CallAnswer;
import com.im.proto.body.CallEvent;
import com.im.proto.body.CallHangup;
import com.im.proto.body.CallInvite;
import com.im.proto.body.CallMediaType;
import com.im.proto.body.CallNotify;
import com.im.proto.body.CallSignal;
import com.im.proto.body.IceServer;
import com.im.proto.rpc.ConnCtx;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 通话信令编排（D45）：状态机迁移 + CALL_NOTIFY 扇出 + CDR 落库。
 *
 * <p>服务端不解析 SDP/ICE（payload 透传）；所有推送 need_ack=true（见 CallPushService）。
 */
@Service
public class CallService {

  private static final Logger log = LoggerFactory.getLogger(CallService.class);

  private final CallSessionService sessions;
  private final CallPushService push;
  private final TurnCredentialService turn;
  private final CallConversationClient conversationClient;
  private final CallRecordMapper callRecordMapper;
  private final CallProperties properties;

  public CallService(CallSessionService sessions,
      CallPushService push,
      TurnCredentialService turn,
      CallConversationClient conversationClient,
      CallRecordMapper callRecordMapper,
      CallProperties properties) {
    this.sessions = sessions;
    this.push = push;
    this.turn = turn;
    this.conversationClient = conversationClient;
    this.callRecordMapper = callRecordMapper;
    this.properties = properties;
  }

  // ─── INVITE ───────────────────────────────────────────────

  public CallAck invite(ConnCtx ctx, CallInvite req) {
    long tenantId = ctx.getTenantId();
    long callerId = ctx.getUserId();
    long calleeId = req.getCalleeUserId();
    long groupId = req.getGroupId();
    if (req.getClientCallId().isBlank()
        || (calleeId > 0 && groupId > 0)
        || (calleeId <= 0 && groupId <= 0)
        || calleeId == callerId) {
      return ack(ErrorCode.VALIDATION_FAILED, "", List.of());
    }

    // 幂等：弱网重发同 client_call_id 返回既有呼叫
    Optional<CallSession> existing = sessions.findByClientCallId(tenantId, req.getClientCallId());
    if (existing.isPresent() && existing.get().callerUserId() == callerId) {
      return ack(ErrorCode.OK, existing.get().callId(), existing.get().groupId(),
          turn.iceServersFor(tenantId, callerId));
    }

    if (groupId > 0) {
      return inviteGroup(ctx, groupId, req.getMediaValue(), req.getClientCallId());
    }

    Optional<CallSession> created = sessions.create(
        tenantId, callerId, calleeId, req.getMediaValue(), req.getClientCallId());
    if (created.isEmpty()) {
      return ack(ErrorCode.CALL_BUSY, "", List.of());
    }
    CallSession session = created.get();

    // 推被叫全端振铃；全端离线也保持呼叫态，由 ring-timeout 统一结束，避免主叫一拨即断。
    int online = push.notifyUsers(
        List.of(calleeId),
        notifyBuilder(session, CallEvent.CALL_EVENT_INVITE, callerId)
            .addAllIceServers(turn.iceServersFor(tenantId, calleeId))
            .build());
    if (online == 0) {
      log.info("call invite target offline, wait timeout, tenant_id={}, call_id={}, caller={}, callee={}",
          tenantId, session.callId(), callerId, calleeId);
    }
    log.info("call invited, tenant_id={}, call_id={}, caller={}, callee={}, online_ends={}",
        tenantId, session.callId(), callerId, calleeId, online);
    return ack(ErrorCode.OK, session.callId(), turn.iceServersFor(tenantId, callerId));
  }

  private CallAck inviteGroup(ConnCtx ctx, long groupId, int media, String clientCallId) {
    long tenantId = ctx.getTenantId();
    long callerId = ctx.getUserId();
    GroupCallTarget target;
    try {
      target = conversationClient.resolveGroupTarget(ctx, groupId);
    } catch (ImException ex) {
      return ack(ex.errorCode(), "", List.of());
    }
    if (target.memberUserIds().isEmpty()) {
      return ack(ErrorCode.VALIDATION_FAILED, "", List.of());
    }
    if (target.memberUserIds().size() + 1 > properties.groupCallMaxMembers()) {
      return ack(ErrorCode.VALIDATION_FAILED, "", target.groupId(), List.of());
    }
    Optional<CallSession> created = sessions.createGroup(
        tenantId,
        callerId,
        target.groupId(),
        target.memberUserIds(),
        media,
        clientCallId);
    if (created.isEmpty()) {
      return ack(ErrorCode.CALL_BUSY, "", List.of());
    }
    CallSession session = created.get();
    int online = notifyUsersWithRecipientIce(
        tenantId,
        target.memberUserIds(),
        session,
        CallEvent.CALL_EVENT_INVITE,
        callerId);
    if (online == 0) {
      log.info("group call invite targets offline, wait timeout, tenant_id={}, call_id={}, caller={}, group_id={}",
          tenantId, session.callId(), callerId, target.groupId());
    }
    log.info("group call invited, tenant_id={}, call_id={}, caller={}, group_id={}, online_ends={}",
        tenantId, session.callId(), callerId, target.groupId(), online);
    return ack(ErrorCode.OK, session.callId(), session.groupId(),
        turn.iceServersFor(tenantId, callerId));
  }

  // ─── ANSWER ───────────────────────────────────────────────

  public CallAck answer(ConnCtx ctx, CallAnswer req) {
    long tenantId = ctx.getTenantId();
    Optional<CallSession> found = sessions.find(tenantId, req.getCallId());
    if (found.isEmpty()) {
      return ack(ErrorCode.CALL_NOT_FOUND, req.getCallId(), List.of());
    }
    CallSession session = found.get();
    if (!canAnswer(session, ctx.getUserId())) {
      return ack(ErrorCode.CALL_STATE_INVALID, session.callId(), session.groupId(), List.of());
    }

    if (!req.getAccept()) {
      if (session.groupCall()) {
        stopOtherCalleeEnds(ctx, session);
        return ack(ErrorCode.OK, session.callId(), session.groupId(), List.of());
      }
      // 拒接：任一端 reject 即整体拒接
      if (sessions.end(tenantId, session)) {
        saveRecord(session, CallRecordEntity.RESULT_REJECTED, 0);
        push.notifyUsers(List.of(session.callerUserId()),
            notifyBuilder(session, CallEvent.CALL_EVENT_REJECTED, session.calleeUserId()).build());
        stopOtherCalleeEnds(ctx, session);
      }
      return ack(ErrorCode.OK, session.callId(), List.of());
    }

    // 接听：CAS 只有一个端赢
    Optional<CallSession> accepted = sessions.accept(tenantId, session, ctx.getUserId());
    if (accepted.isEmpty()) {
      return ack(ErrorCode.CALL_STATE_INVALID, session.callId(), session.groupId(), List.of());
    }
    session = accepted.get();
    if (session.groupCall()) {
      session = sessions.find(tenantId, session.callId()).orElse(session);
    }
    List<Long> acceptedTargets = acceptedNotifyTargets(session, ctx.getUserId());
    if (session.groupCall()) {
      notifyUsersWithRecipientIce(
          tenantId,
          acceptedTargets,
          session,
          CallEvent.CALL_EVENT_ACCEPTED,
          ctx.getUserId());
    } else {
      push.notifyUsers(acceptedTargets,
          notifyBuilder(session, CallEvent.CALL_EVENT_ACCEPTED, ctx.getUserId())
              .addAllIceServers(turn.iceServersFor(tenantId, session.callerUserId()))
              .build());
    }
    stopOtherCalleeEnds(ctx, session);
    log.info("call accepted, tenant_id={}, call_id={}, answer_conn={}",
        tenantId, session.callId(), ctx.getConnId());
    return ack(ErrorCode.OK, session.callId(), session.groupId(),
        turn.iceServersFor(tenantId, ctx.getUserId()));
  }

  // ─── SIGNAL（SDP/ICE 中转）───────────────────────────────

  public CallAck signal(ConnCtx ctx, CallSignal req) {
    long tenantId = ctx.getTenantId();
    Optional<CallSession> found = sessions.find(tenantId, req.getCallId());
    if (found.isEmpty()) {
      return ack(ErrorCode.CALL_NOT_FOUND, req.getCallId(), List.of());
    }
    CallSession session = found.get();
    if (!session.isParticipant(ctx.getUserId()) || !session.active()) {
      return ack(ErrorCode.CALL_STATE_INVALID, session.callId(), List.of());
    }
    if (session.groupCall()) {
      long targetUserId = groupSignalTarget(session, ctx.getUserId(), req.getTargetUserId());
      if (targetUserId <= 0
          || targetUserId == ctx.getUserId()
          || !session.isActiveParticipant(ctx.getUserId())
          || !session.isActiveParticipant(targetUserId)) {
        return ack(ErrorCode.CALL_STATE_INVALID, session.callId(), session.groupId(), List.of());
      }
      push.notifyUsers(
          List.of(targetUserId),
          notifyBuilder(session, CallEvent.CALL_EVENT_SIGNAL, ctx.getUserId())
              .setSignal(req)
              .build());
      return ack(ErrorCode.OK, session.callId(), session.groupId(), List.of());
    }
    push.notifyUsers(
        List.of(session.peerOf(ctx.getUserId())),
        notifyBuilder(session, CallEvent.CALL_EVENT_SIGNAL, ctx.getUserId())
            .setSignal(req)
            .build());
    return ack(ErrorCode.OK, session.callId(), List.of());
  }

  // ─── HANGUP（取消/挂断）──────────────────────────────────

  public CallAck hangup(ConnCtx ctx, CallHangup req) {
    long tenantId = ctx.getTenantId();
    Optional<CallSession> found = sessions.find(tenantId, req.getCallId());
    if (found.isEmpty()) {
      return ack(ErrorCode.CALL_NOT_FOUND, req.getCallId(), List.of());
    }
    CallSession session = found.get();
    if (!session.isParticipant(ctx.getUserId())) {
      return ack(ErrorCode.CALL_STATE_INVALID, session.callId(), session.groupId(), List.of());
    }
    if (session.groupCall() && session.inviting() && ctx.getUserId() != session.callerUserId()) {
      return ack(ErrorCode.OK, session.callId(), session.groupId(), List.of());
    }
    if (session.groupCall() && session.active() && ctx.getUserId() != session.callerUserId()) {
      return leaveGroupCall(ctx, session);
    }
    if (!sessions.end(tenantId, session)) {
      return ack(ErrorCode.OK, session.callId(), session.groupId(), List.of()); // 已被并发终结，幂等返回
    }

    long now = System.currentTimeMillis();
    if (session.inviting()) {
      // 振铃期：主叫挂 = 取消（推被叫全端停铃）；被叫挂等价拒接（推主叫）
      boolean byCaller = ctx.getUserId() == session.callerUserId();
      saveRecord(session,
          byCaller ? CallRecordEntity.RESULT_CANCELED : CallRecordEntity.RESULT_REJECTED, 0);
      push.notifyUsers(
          ringingNotifyTargets(session, ctx.getUserId()),
          notifyBuilder(session,
              byCaller ? CallEvent.CALL_EVENT_CANCELED : CallEvent.CALL_EVENT_REJECTED,
              ctx.getUserId()).build());
      if (!byCaller) {
        stopOtherCalleeEnds(ctx, session);
      }
    } else {
      int duration = session.durationSec(now);
      saveRecord(session, CallRecordEntity.RESULT_COMPLETED, duration);
      push.notifyUsers(
          hangupNotifyTargets(session, ctx.getUserId()),
          notifyBuilder(session, CallEvent.CALL_EVENT_HANGUP, ctx.getUserId())
              .setDurationSec(duration)
              .build());
      log.info("call ended, tenant_id={}, call_id={}, duration_sec={}",
          tenantId, session.callId(), duration);
    }
    return ack(ErrorCode.OK, session.callId(), session.groupId(), List.of());
  }

  // ─── 振铃超时（sweeper 调用，已 claim 处理权）─────────────

  public void timeoutRing(long tenantId, String callId) {
    Optional<CallSession> found = sessions.find(tenantId, callId);
    if (found.isEmpty() || !found.get().inviting()) {
      return; // 已接听或已终结
    }
    CallSession session = found.get();
    if (!sessions.end(tenantId, session)) {
      return;
    }
    saveRecord(session, CallRecordEntity.RESULT_TIMEOUT, 0);
    CallNotify notify = notifyBuilder(session, CallEvent.CALL_EVENT_TIMEOUT, 0L).build();
    push.notifyUsers(timeoutTargets(session), notify);
    log.info("call ring timeout, tenant_id={}, call_id={}", tenantId, callId);
  }

  // ─── 内部 ─────────────────────────────────────────────────

  private CallAck leaveGroupCall(ConnCtx ctx, CallSession session) {
    long tenantId = ctx.getTenantId();
    long actorId = ctx.getUserId();
    GroupLeaveResult leave = sessions.leaveGroup(tenantId, session, actorId);
    if (leave == GroupLeaveResult.NOT_ACTIVE) {
      return ack(ErrorCode.OK, session.callId(), session.groupId(), List.of());
    }

    long now = System.currentTimeMillis();
    int duration = session.durationSec(now);
    CallSession afterLeave = sessions.find(tenantId, session.callId())
        .orElse(session.withoutActiveUser(actorId));
    List<Long> notifyTargets = afterLeave.activeParticipantUserIds().stream()
        .filter(id -> id != actorId)
        .toList();
    if (!notifyTargets.isEmpty()) {
      push.notifyUsers(
          notifyTargets,
          notifyBuilder(afterLeave, CallEvent.CALL_EVENT_HANGUP, actorId)
              .setDurationSec(duration)
              .build());
    }
    if (leave == GroupLeaveResult.LAST_ACTIVE && sessions.end(tenantId, afterLeave)) {
      saveRecord(afterLeave, CallRecordEntity.RESULT_COMPLETED, duration);
      log.info("group call ended after last participant left, tenant_id={}, call_id={}, duration_sec={}",
          tenantId, session.callId(), duration);
    }
    return ack(ErrorCode.OK, session.callId(), session.groupId(), List.of());
  }

  /** 被叫其他端停铃（排除操作端连接）。 */
  private void stopOtherCalleeEnds(ConnCtx ctx, CallSession session) {
    if (session.groupCall()) {
      push.notifyUsers(
          List.of(ctx.getUserId()),
          notifyBuilder(session, CallEvent.CALL_EVENT_ANSWERED_ELSEWHERE, session.callerUserId())
              .build(),
          ctx.getUserId(),
          ctx.getConnId());
      return;
    }
    push.notifyUsers(
        List.of(session.calleeUserId()),
        notifyBuilder(session, CallEvent.CALL_EVENT_ANSWERED_ELSEWHERE, session.callerUserId())
            .build(),
        ctx.getUserId(),
        ctx.getConnId());
  }

  private CallNotify.Builder notifyBuilder(CallSession session, CallEvent event, long peerUserId) {
    CallNotify.Builder builder = CallNotify.newBuilder()
        .setCallId(session.callId())
        .setEvent(event)
        .setPeerUserId(peerUserId)
        .setMediaValue(session.media())
        .setServerTs(System.currentTimeMillis());
    if (session.groupId() > 0) {
      builder.setGroupId(session.groupId())
          .addAllParticipantUserIds(participantSnapshot(session, event));
    }
    return builder;
  }

  private int notifyUsersWithRecipientIce(long tenantId,
      Collection<Long> userIds,
      CallSession session,
      CallEvent event,
      long peerUserId) {
    int online = 0;
    for (Long userId : userIds) {
      if (userId == null || userId <= 0) {
        continue;
      }
      online += push.notifyUsers(
          List.of(userId),
          notifyBuilder(session, event, peerUserId)
              .addAllIceServers(turn.iceServersFor(tenantId, userId))
              .build());
    }
    return online;
  }

  private void saveRecord(CallSession session, int result, int durationSec) {
    try {
      CallRecordEntity entity = new CallRecordEntity();
      entity.setTenantId(com.im.common.tenant.TenantContext.requiredTenantId());
      entity.setCallId(session.callId());
      entity.setCallerUserId(session.callerUserId());
      entity.setCalleeUserId(session.calleeUserId());
      if (session.groupId() > 0) {
        entity.setGroupId(session.groupId());
      }
      entity.setMediaType(session.media() == 0
          ? CallMediaType.CALL_MEDIA_VOICE_VALUE : session.media());
      entity.setResult(result);
      if (session.answerAtMs() > 0) {
        entity.setConnectedAt(toLocal(session.answerAtMs()));
      }
      entity.setEndedAt(toLocal(System.currentTimeMillis()));
      entity.setDurationSec(durationSec);
      callRecordMapper.insert(entity);
    } catch (Exception ex) {
      // CDR 落库失败不阻断信令主流程（唯一键冲突=并发重复终结，可安全忽略）
      log.warn("failed to save call record, call_id={}", session.callId(), ex);
    }
  }

  private static LocalDateTime toLocal(long epochMs) {
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
  }

  private boolean canAnswer(CallSession session, long userId) {
    if (!session.groupCall()) {
      return userId == session.calleeUserId();
    }
    return userId != session.callerUserId() && session.invitedUserIds().contains(userId);
  }

  private long groupSignalTarget(CallSession session, long senderUserId, long requestedTargetUserId) {
    if (requestedTargetUserId > 0) {
      return requestedTargetUserId;
    }
    List<Long> peers = session.activeParticipantUserIds().stream()
        .filter(id -> id != senderUserId)
        .toList();
    return peers.size() == 1 ? peers.getFirst() : 0L;
  }

  private List<Long> acceptedNotifyTargets(CallSession session, long actorUserId) {
    if (!session.groupCall()) {
      return List.of(session.callerUserId());
    }
    return session.activeParticipantUserIds().stream()
        .filter(id -> id != actorUserId)
        .toList();
  }

  private List<Long> hangupNotifyTargets(CallSession session, long actorUserId) {
    if (!session.groupCall()) {
      return List.of(session.peerOf(actorUserId));
    }
    return session.groupNotifyUserIds().stream()
        .filter(id -> id != actorUserId)
        .toList();
  }

  private List<Long> participantSnapshot(CallSession session, CallEvent event) {
    if (!session.groupCall()) {
      return List.of();
    }
    if (event == CallEvent.CALL_EVENT_INVITE
        || event == CallEvent.CALL_EVENT_CANCELED
        || event == CallEvent.CALL_EVENT_TIMEOUT) {
      return java.util.stream.Stream
          .concat(java.util.stream.Stream.of(session.callerUserId()),
              session.invitedUserIds().stream())
          .distinct()
          .toList();
    }
    return session.activeParticipantUserIds();
  }

  private List<Long> ringingNotifyTargets(CallSession session, long actorUserId) {
    if (!session.groupCall()) {
      return List.of(session.peerOf(actorUserId));
    }
    return session.invitedUserIds();
  }

  private List<Long> timeoutTargets(CallSession session) {
    if (!session.groupCall()) {
      return List.of(session.callerUserId(), session.calleeUserId());
    }
    return java.util.stream.Stream
        .concat(java.util.stream.Stream.of(session.callerUserId()), session.invitedUserIds().stream())
        .distinct()
        .toList();
  }

  private static CallAck ack(ErrorCode code, String callId, List<IceServer> iceServers) {
    return ack(code, callId, 0L, iceServers);
  }

  private static CallAck ack(ErrorCode code, String callId, long groupId, List<IceServer> iceServers) {
    return CallAck.newBuilder()
        .setCode(code.code())
        .setMessage(code.defaultMessage())
        .setCallId(callId)
        .addAllIceServers(iceServers)
        .setGroupId(groupId)
        .setServerTs(System.currentTimeMillis())
        .build();
  }
}
