package com.im.call.service;

import com.im.call.dao.entity.CallRecordEntity;
import com.im.call.dao.mapper.CallRecordMapper;
import com.im.common.error.ErrorCode;
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
  private final CallRecordMapper callRecordMapper;

  public CallService(CallSessionService sessions,
      CallPushService push,
      TurnCredentialService turn,
      CallRecordMapper callRecordMapper) {
    this.sessions = sessions;
    this.push = push;
    this.turn = turn;
    this.callRecordMapper = callRecordMapper;
  }

  // ─── INVITE ───────────────────────────────────────────────

  public CallAck invite(ConnCtx ctx, CallInvite req) {
    long tenantId = ctx.getTenantId();
    long callerId = ctx.getUserId();
    long calleeId = req.getCalleeUserId();
    if (calleeId <= 0 || calleeId == callerId || req.getClientCallId().isBlank()) {
      return ack(ErrorCode.VALIDATION_FAILED, "", List.of());
    }

    // 幂等：弱网重发同 client_call_id 返回既有呼叫
    Optional<CallSession> existing = sessions.findByClientCallId(tenantId, req.getClientCallId());
    if (existing.isPresent() && existing.get().callerUserId() == callerId) {
      return ack(ErrorCode.OK, existing.get().callId(), turn.iceServersFor(tenantId, callerId));
    }

    Optional<CallSession> created = sessions.create(
        tenantId, callerId, calleeId, req.getMediaValue(), req.getClientCallId());
    if (created.isEmpty()) {
      return ack(ErrorCode.CALL_BUSY, "", List.of());
    }
    CallSession session = created.get();

    // 推被叫全端振铃；全端离线 = 代答（MVP 无离线推送通道）
    int online = push.notifyUsers(
        List.of(calleeId),
        notifyBuilder(session, CallEvent.CALL_EVENT_INVITE, callerId)
            .addAllIceServers(turn.iceServersFor(tenantId, calleeId))
            .build());
    if (online == 0) {
      sessions.end(tenantId, session);
      return ack(ErrorCode.CALL_PEER_OFFLINE, session.callId(), List.of());
    }
    log.info("call invited, tenant_id={}, call_id={}, caller={}, callee={}, online_ends={}",
        tenantId, session.callId(), callerId, calleeId, online);
    return ack(ErrorCode.OK, session.callId(), turn.iceServersFor(tenantId, callerId));
  }

  // ─── ANSWER ───────────────────────────────────────────────

  public CallAck answer(ConnCtx ctx, CallAnswer req) {
    long tenantId = ctx.getTenantId();
    Optional<CallSession> found = sessions.find(tenantId, req.getCallId());
    if (found.isEmpty()) {
      return ack(ErrorCode.CALL_NOT_FOUND, req.getCallId(), List.of());
    }
    CallSession session = found.get();
    if (ctx.getUserId() != session.calleeUserId()) {
      return ack(ErrorCode.CALL_STATE_INVALID, session.callId(), List.of());
    }

    if (!req.getAccept()) {
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
    if (!sessions.accept(tenantId, session)) {
      return ack(ErrorCode.CALL_STATE_INVALID, session.callId(), List.of());
    }
    push.notifyUsers(List.of(session.callerUserId()),
        notifyBuilder(session, CallEvent.CALL_EVENT_ACCEPTED, session.calleeUserId())
            .addAllIceServers(turn.iceServersFor(tenantId, session.callerUserId()))
            .build());
    stopOtherCalleeEnds(ctx, session);
    log.info("call accepted, tenant_id={}, call_id={}, answer_conn={}",
        tenantId, session.callId(), ctx.getConnId());
    return ack(ErrorCode.OK, session.callId(),
        turn.iceServersFor(tenantId, session.calleeUserId()));
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
      return ack(ErrorCode.CALL_STATE_INVALID, session.callId(), List.of());
    }
    if (!sessions.end(tenantId, session)) {
      return ack(ErrorCode.OK, session.callId(), List.of()); // 已被并发终结，幂等返回
    }

    long now = System.currentTimeMillis();
    if (session.inviting()) {
      // 振铃期：主叫挂 = 取消（推被叫全端停铃）；被叫挂等价拒接（推主叫）
      boolean byCaller = ctx.getUserId() == session.callerUserId();
      saveRecord(session,
          byCaller ? CallRecordEntity.RESULT_CANCELED : CallRecordEntity.RESULT_REJECTED, 0);
      push.notifyUsers(
          List.of(session.peerOf(ctx.getUserId())),
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
          List.of(session.peerOf(ctx.getUserId())),
          notifyBuilder(session, CallEvent.CALL_EVENT_HANGUP, ctx.getUserId())
              .setDurationSec(duration)
              .build());
      log.info("call ended, tenant_id={}, call_id={}, duration_sec={}",
          tenantId, session.callId(), duration);
    }
    return ack(ErrorCode.OK, session.callId(), List.of());
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
    push.notifyUsers(List.of(session.callerUserId(), session.calleeUserId()), notify);
    log.info("call ring timeout, tenant_id={}, call_id={}", tenantId, callId);
  }

  // ─── 内部 ─────────────────────────────────────────────────

  /** 被叫其他端停铃（排除操作端连接）。 */
  private void stopOtherCalleeEnds(ConnCtx ctx, CallSession session) {
    push.notifyUsers(
        List.of(session.calleeUserId()),
        notifyBuilder(session, CallEvent.CALL_EVENT_ANSWERED_ELSEWHERE, session.callerUserId())
            .build(),
        ctx.getUserId(),
        ctx.getConnId());
  }

  private CallNotify.Builder notifyBuilder(CallSession session, CallEvent event, long peerUserId) {
    return CallNotify.newBuilder()
        .setCallId(session.callId())
        .setEvent(event)
        .setPeerUserId(peerUserId)
        .setMediaValue(session.media())
        .setServerTs(System.currentTimeMillis());
  }

  private void saveRecord(CallSession session, int result, int durationSec) {
    try {
      CallRecordEntity entity = new CallRecordEntity();
      entity.setTenantId(com.im.common.tenant.TenantContext.requiredTenantId());
      entity.setCallId(session.callId());
      entity.setCallerUserId(session.callerUserId());
      entity.setCalleeUserId(session.calleeUserId());
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

  private static CallAck ack(ErrorCode code, String callId, List<IceServer> iceServers) {
    return CallAck.newBuilder()
        .setCode(code.code())
        .setMessage(code.defaultMessage())
        .setCallId(callId)
        .addAllIceServers(iceServers)
        .setServerTs(System.currentTimeMillis())
        .build();
  }
}
