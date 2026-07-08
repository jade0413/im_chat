package com.im.call.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.im.call.config.CallProperties;
import com.im.call.dao.mapper.CallRecordMapper;
import com.im.call.service.CallConversationClient.GroupCallTarget;
import com.im.call.service.CallSessionService.GroupLeaveResult;
import com.im.common.error.ErrorCode;
import com.im.common.tenant.TenantContext;
import com.im.proto.body.CallAck;
import com.im.proto.body.CallAnswer;
import com.im.proto.body.CallEvent;
import com.im.proto.body.CallHangup;
import com.im.proto.body.CallInvite;
import com.im.proto.body.CallMediaType;
import com.im.proto.body.CallNotify;
import com.im.proto.body.CallSignal;
import com.im.proto.body.CallSignalType;
import com.im.proto.body.IceServer;
import com.im.proto.rpc.ConnCtx;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("unchecked")
class CallServiceTest {

  private CallSessionService sessions;
  private CallPushService push;
  private TurnCredentialService turn;
  private CallConversationClient conversationClient;
  private CallService service;

  @BeforeEach
  void setUp() {
    sessions = org.mockito.Mockito.mock(CallSessionService.class);
    push = org.mockito.Mockito.mock(CallPushService.class);
    turn = org.mockito.Mockito.mock(TurnCredentialService.class);
    conversationClient = org.mockito.Mockito.mock(CallConversationClient.class);
    CallRecordMapper callRecordMapper = org.mockito.Mockito.mock(CallRecordMapper.class);
    service = new CallService(
        sessions,
        push,
        turn,
        conversationClient,
        callRecordMapper,
        callProperties(9));
  }

  @Test
  void inviteKeepsRingingWhenCalleeOffline() throws Exception {
    CallSession session = new CallSession(
        "call-offline",
        1L,
        2L,
        0L,
        List.of(),
        List.of(),
        CallMediaType.CALL_MEDIA_VOICE_VALUE,
        CallSession.STATE_INVITING,
        "client-call-offline",
        System.currentTimeMillis(),
        0L);
    when(sessions.create(
        1L,
        1L,
        2L,
        CallMediaType.CALL_MEDIA_VOICE_VALUE,
        "client-call-offline"))
        .thenReturn(Optional.of(session));
    when(turn.iceServersFor(1L, 1L)).thenReturn(List.of(ice("turn-caller")));
    when(turn.iceServersFor(1L, 2L)).thenReturn(List.of(ice("turn-callee")));
    when(push.notifyUsers(anyCollection(), any(CallNotify.class))).thenReturn(0);

    CallAck ack = TenantContext.callWithTenant(1L,
        () -> service.invite(
            conn(1L),
            CallInvite.newBuilder()
                .setCalleeUserId(2L)
                .setMedia(CallMediaType.CALL_MEDIA_VOICE)
                .setClientCallId("client-call-offline")
                .build()));

    assertEquals(ErrorCode.OK.code(), ack.getCode());
    assertEquals("call-offline", ack.getCallId());
    assertEquals("turn-caller", ack.getIceServers(0).getUsername());
    verify(sessions, never()).end(1L, session);
  }

  @Test
  void groupInviteSendsRecipientScopedIceServers() throws Exception {
    CallSession session = new CallSession(
        "call-1",
        1L,
        0L,
        100L,
        List.of(2L, 3L),
        List.of(),
        CallMediaType.CALL_MEDIA_VIDEO_VALUE,
        CallSession.STATE_INVITING,
        "client-call-1",
        System.currentTimeMillis(),
        0L);
    when(conversationClient.resolveGroupTarget(any(ConnCtx.class), eq(100L)))
        .thenReturn(new GroupCallTarget(100L, 501L, List.of(2L, 3L)));
    when(sessions.createGroup(
        1L,
        1L,
        100L,
        List.of(2L, 3L),
        CallMediaType.CALL_MEDIA_VIDEO_VALUE,
        "client-call-1"))
        .thenReturn(Optional.of(session));
    when(turn.iceServersFor(1L, 1L)).thenReturn(List.of(ice("turn-1")));
    when(turn.iceServersFor(1L, 2L)).thenReturn(List.of(ice("turn-2")));
    when(turn.iceServersFor(1L, 3L)).thenReturn(List.of(ice("turn-3")));
    when(push.notifyUsers(anyCollection(), any(CallNotify.class))).thenReturn(1);

    CallAck ack = TenantContext.callWithTenant(1L,
        () -> service.invite(
            conn(1L),
            CallInvite.newBuilder()
                .setGroupId(100L)
                .setMedia(CallMediaType.CALL_MEDIA_VIDEO)
                .setClientCallId("client-call-1")
                .build()));

    assertEquals(ErrorCode.OK.code(), ack.getCode());
    assertEquals(100L, ack.getGroupId());
    assertEquals("turn-1", ack.getIceServers(0).getUsername());

    ArgumentCaptor<Collection<Long>> targets = ArgumentCaptor.forClass(Collection.class);
    ArgumentCaptor<CallNotify> notify = ArgumentCaptor.forClass(CallNotify.class);
    verify(push, times(2)).notifyUsers(targets.capture(), notify.capture());
    assertIterableEquals(List.of(2L), targets.getAllValues().get(0));
    assertEquals("turn-2", notify.getAllValues().get(0).getIceServers(0).getUsername());
    assertIterableEquals(List.of(3L), targets.getAllValues().get(1));
    assertEquals("turn-3", notify.getAllValues().get(1).getIceServers(0).getUsername());
  }

  @Test
  void groupInviteRejectsWhenGroupCallMemberLimitExceeded() throws Exception {
    when(conversationClient.resolveGroupTarget(any(ConnCtx.class), eq(100L)))
        .thenReturn(new GroupCallTarget(100L, 501L, List.of(2L, 3L, 4L, 5L)));
    service = new CallService(
        sessions,
        push,
        turn,
        conversationClient,
        org.mockito.Mockito.mock(CallRecordMapper.class),
        callProperties(3));

    CallAck ack = TenantContext.callWithTenant(1L,
        () -> service.invite(
            conn(1L),
            CallInvite.newBuilder()
                .setGroupId(100L)
                .setMedia(CallMediaType.CALL_MEDIA_VIDEO)
                .setClientCallId("client-call-large")
                .build()));

    assertEquals(ErrorCode.VALIDATION_FAILED.code(), ack.getCode());
    assertEquals(100L, ack.getGroupId());
    verify(sessions, never()).createGroup(
        eq(1L), eq(1L), eq(100L), any(), eq(CallMediaType.CALL_MEDIA_VIDEO_VALUE), any());
    verify(push, never()).notifyUsers(anyCollection(), any(CallNotify.class));
  }

  @Test
  void groupAcceptSendsRecipientScopedIceServers() throws Exception {
    CallSession before = activeGroupSession(List.of(2L));
    CallSession staleAccepted = activeGroupSession(List.of(3L));
    CallSession freshAccepted = activeGroupSession(List.of(2L, 3L));
    when(sessions.find(1L, "call-1")).thenReturn(Optional.of(before), Optional.of(freshAccepted));
    when(sessions.accept(1L, before, 3L)).thenReturn(Optional.of(staleAccepted));
    when(turn.iceServersFor(1L, 1L)).thenReturn(List.of(ice("turn-1")));
    when(turn.iceServersFor(1L, 2L)).thenReturn(List.of(ice("turn-2")));
    when(turn.iceServersFor(1L, 3L)).thenReturn(List.of(ice("turn-3")));
    when(push.notifyUsers(anyCollection(), any(CallNotify.class))).thenReturn(1);

    CallAck ack = TenantContext.callWithTenant(1L,
        () -> service.answer(
            conn(3L),
            CallAnswer.newBuilder()
                .setCallId("call-1")
                .setAccept(true)
                .build()));

    assertEquals(ErrorCode.OK.code(), ack.getCode());
    assertEquals(100L, ack.getGroupId());
    assertEquals("turn-3", ack.getIceServers(0).getUsername());

    ArgumentCaptor<Collection<Long>> targets = ArgumentCaptor.forClass(Collection.class);
    ArgumentCaptor<CallNotify> notify = ArgumentCaptor.forClass(CallNotify.class);
    verify(push, times(2)).notifyUsers(targets.capture(), notify.capture());
    assertIterableEquals(List.of(1L), targets.getAllValues().get(0));
    assertEquals("turn-1", notify.getAllValues().get(0).getIceServers(0).getUsername());
    assertIterableEquals(List.of(2L), targets.getAllValues().get(1));
    assertEquals("turn-2", notify.getAllValues().get(1).getIceServers(0).getUsername());
  }

  @Test
  void groupRejectStopsRingingOnOtherDevicesOfSameUser() {
    CallSession ringing = ringingGroupSession();
    when(sessions.find(1L, "call-1")).thenReturn(Optional.of(ringing));

    CallAck ack = service.answer(
        conn(3L),
        CallAnswer.newBuilder()
            .setCallId("call-1")
            .setAccept(false)
            .build());

    assertEquals(ErrorCode.OK.code(), ack.getCode());
    assertEquals(100L, ack.getGroupId());
    verify(push).notifyUsers(
        eq(List.of(3L)),
        any(CallNotify.class),
        eq(3L),
        eq("conn-3"));
  }

  @Test
  void groupSignalForwardsOnlyToJoinedTarget() {
    CallSession session = activeGroupSession(List.of(2L, 3L));
    when(sessions.find(1L, "call-1")).thenReturn(Optional.of(session));
    when(push.notifyUsers(anyCollection(), any(CallNotify.class))).thenReturn(1);

    CallAck ack = service.signal(
        conn(2L),
        CallSignal.newBuilder()
            .setCallId("call-1")
            .setType(CallSignalType.CALL_SDP_OFFER)
            .setPayload("offer")
            .setTargetUserId(3L)
            .build());

    assertEquals(ErrorCode.OK.code(), ack.getCode());
    assertEquals(100L, ack.getGroupId());

    ArgumentCaptor<Collection<Long>> targets = ArgumentCaptor.forClass(Collection.class);
    ArgumentCaptor<CallNotify> notify = ArgumentCaptor.forClass(CallNotify.class);
    verify(push).notifyUsers(targets.capture(), notify.capture());
    assertIterableEquals(List.of(3L), targets.getValue());
    assertEquals(CallEvent.CALL_EVENT_SIGNAL, notify.getValue().getEvent());
    assertEquals(2L, notify.getValue().getPeerUserId());
    assertEquals(3L, notify.getValue().getSignal().getTargetUserId());
    assertIterableEquals(List.of(1L, 2L, 3L), notify.getValue().getParticipantUserIdsList());
  }

  @Test
  void groupSignalInfersTargetWhenOnlyOnePeerIsActive() {
    CallSession session = activeGroupSession(List.of(2L));
    when(sessions.find(1L, "call-1")).thenReturn(Optional.of(session));
    when(push.notifyUsers(anyCollection(), any(CallNotify.class))).thenReturn(1);

    CallAck ack = service.signal(
        conn(1L),
        CallSignal.newBuilder()
            .setCallId("call-1")
            .setType(CallSignalType.CALL_SDP_OFFER)
            .setPayload("offer")
            .build());

    assertEquals(ErrorCode.OK.code(), ack.getCode());

    ArgumentCaptor<Collection<Long>> targets = ArgumentCaptor.forClass(Collection.class);
    ArgumentCaptor<CallNotify> notify = ArgumentCaptor.forClass(CallNotify.class);
    verify(push).notifyUsers(targets.capture(), notify.capture());
    assertIterableEquals(List.of(2L), targets.getValue());
    assertEquals(1L, notify.getValue().getPeerUserId());
    assertEquals(0L, notify.getValue().getSignal().getTargetUserId());
  }

  @Test
  void groupSignalRejectsTargetThatHasNotJoined() {
    CallSession session = activeGroupSession(List.of(2L, 3L));
    when(sessions.find(1L, "call-1")).thenReturn(Optional.of(session));

    CallAck ack = service.signal(
        conn(2L),
        CallSignal.newBuilder()
            .setCallId("call-1")
            .setType(CallSignalType.CALL_SDP_OFFER)
            .setPayload("offer")
            .setTargetUserId(4L)
            .build());

    assertEquals(ErrorCode.CALL_STATE_INVALID.code(), ack.getCode());
    verify(push, never()).notifyUsers(anyCollection(), any(CallNotify.class));
  }

  @Test
  void groupMemberHangupLeavesWithoutEndingSession() throws Exception {
    CallSession session = activeGroupSession(List.of(2L, 3L));
    CallSession afterLeave = activeGroupSession(List.of(3L));
    when(sessions.find(1L, "call-1")).thenReturn(Optional.of(session), Optional.of(afterLeave));
    when(sessions.leaveGroup(1L, session, 2L)).thenReturn(GroupLeaveResult.LEFT);
    when(push.notifyUsers(anyCollection(), any(CallNotify.class))).thenReturn(1);

    CallAck ack = TenantContext.callWithTenant(1L,
        () -> service.hangup(conn(2L), CallHangup.newBuilder().setCallId("call-1").build()));

    assertEquals(ErrorCode.OK.code(), ack.getCode());
    verify(sessions, never()).end(1L, afterLeave);

    ArgumentCaptor<Collection<Long>> targets = ArgumentCaptor.forClass(Collection.class);
    ArgumentCaptor<CallNotify> notify = ArgumentCaptor.forClass(CallNotify.class);
    verify(push).notifyUsers(targets.capture(), notify.capture());
    assertIterableEquals(List.of(1L, 3L), targets.getValue());
    assertEquals(CallEvent.CALL_EVENT_HANGUP, notify.getValue().getEvent());
    assertEquals(2L, notify.getValue().getPeerUserId());
    assertIterableEquals(List.of(1L, 3L), notify.getValue().getParticipantUserIdsList());
  }

  @Test
  void lastGroupParticipantHangupEndsSession() throws Exception {
    CallSession session = activeGroupSession(List.of(2L));
    CallSession afterLeave = activeGroupSession(List.of());
    when(sessions.find(1L, "call-1")).thenReturn(Optional.of(session), Optional.of(afterLeave));
    when(sessions.leaveGroup(1L, session, 2L)).thenReturn(GroupLeaveResult.LAST_ACTIVE);
    when(sessions.end(1L, afterLeave)).thenReturn(true);
    when(push.notifyUsers(anyCollection(), any(CallNotify.class))).thenReturn(1);

    CallAck ack = TenantContext.callWithTenant(1L,
        () -> service.hangup(conn(2L), CallHangup.newBuilder().setCallId("call-1").build()));

    assertEquals(ErrorCode.OK.code(), ack.getCode());
    verify(sessions).end(1L, afterLeave);

    ArgumentCaptor<Collection<Long>> targets = ArgumentCaptor.forClass(Collection.class);
    verify(push).notifyUsers(targets.capture(), any(CallNotify.class));
    assertIterableEquals(List.of(1L), targets.getValue());
  }

  private static CallSession activeGroupSession(List<Long> activeUserIds) {
    long now = System.currentTimeMillis();
    return new CallSession(
        "call-1",
        1L,
        activeUserIds.isEmpty() ? 0L : activeUserIds.get(0),
        100L,
        List.of(2L, 3L, 4L),
        activeUserIds,
        CallMediaType.CALL_MEDIA_VIDEO_VALUE,
        CallSession.STATE_ACTIVE,
        "client-call-1",
        now - 20_000L,
        now - 10_000L);
  }

  private static CallSession ringingGroupSession() {
    long now = System.currentTimeMillis();
    return new CallSession(
        "call-1",
        1L,
        0L,
        100L,
        List.of(2L, 3L, 4L),
        List.of(),
        CallMediaType.CALL_MEDIA_VIDEO_VALUE,
        CallSession.STATE_INVITING,
        "client-call-1",
        now - 10_000L,
        0L);
  }

  private static ConnCtx conn(long userId) {
    return ConnCtx.newBuilder()
        .setTenantId(1L)
        .setUserId(userId)
        .setConnId("conn-" + userId)
        .build();
  }

  private static IceServer ice(String username) {
    return IceServer.newBuilder()
        .addUrls("turn:turn.example.com")
        .setUsername(username)
        .setCredential("credential-" + username)
        .build();
  }

  private static CallProperties callProperties(int groupCallMaxMembers) {
    return new CallProperties(
        Duration.ofSeconds(30),
        Duration.ofHours(4),
        List.of("stun:stun.example.com"),
        List.of(),
        "",
        Duration.ofHours(1),
        groupCallMaxMembers);
  }
}
