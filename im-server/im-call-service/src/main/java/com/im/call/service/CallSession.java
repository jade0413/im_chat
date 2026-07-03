package com.im.call.service;

import java.util.List;
import java.util.stream.Stream;

/** Redis 呼叫会话快照。state：INVITING / ACTIVE（ENDED 即键删除）。 */
public record CallSession(
    String callId,
    long callerUserId,
    long calleeUserId,
    long groupId,
    List<Long> invitedUserIds,
    List<Long> activeUserIds,
    int media,
    String state,
    String clientCallId,
    long inviteAtMs,
    long answerAtMs
) {

  public CallSession {
    invitedUserIds = invitedUserIds == null ? List.of() : List.copyOf(invitedUserIds);
    activeUserIds = activeUserIds == null ? List.of() : List.copyOf(activeUserIds);
  }

  public static final String STATE_INVITING = "INVITING";
  public static final String STATE_ACTIVE = "ACTIVE";

  public boolean isParticipant(long userId) {
    return userId == callerUserId
        || userId == calleeUserId
        || invitedUserIds.contains(userId)
        || activeUserIds.contains(userId);
  }

  public boolean isActiveParticipant(long userId) {
    if (!groupCall()) {
      return active() && (userId == callerUserId || userId == calleeUserId);
    }
    return userId == callerUserId || activeUserIds.contains(userId);
  }

  public long peerOf(long userId) {
    return userId == callerUserId ? calleeUserId : callerUserId;
  }

  public boolean groupCall() {
    return groupId > 0;
  }

  public CallSession withAcceptedCallee(long calleeUserId, long answerAtMs) {
    List<Long> active = groupCall()
        ? Stream.concat(activeUserIds.stream(), Stream.of(calleeUserId))
            .filter(id -> id != null && id > 0 && id != callerUserId)
            .distinct()
            .toList()
        : activeUserIds;
    return new CallSession(
        callId,
        callerUserId,
        this.calleeUserId > 0 ? this.calleeUserId : calleeUserId,
        groupId,
        invitedUserIds,
        active,
        media,
        STATE_ACTIVE,
        clientCallId,
        inviteAtMs,
        this.answerAtMs > 0 ? this.answerAtMs : answerAtMs);
  }

  public CallSession withoutActiveUser(long userId) {
    if (!groupCall()) {
      return this;
    }
    return new CallSession(
        callId,
        callerUserId,
        calleeUserId == userId ? 0L : calleeUserId,
        groupId,
        invitedUserIds,
        activeUserIds.stream().filter(id -> id != userId).toList(),
        media,
        state,
        clientCallId,
        inviteAtMs,
        answerAtMs);
  }

  public List<Long> activeParticipantUserIds() {
    if (!groupCall()) {
      return active() ? List.of(callerUserId, calleeUserId) : List.of(callerUserId);
    }
    return Stream.concat(Stream.of(callerUserId), activeUserIds.stream())
        .filter(id -> id != null && id > 0)
        .distinct()
        .toList();
  }

  public List<Long> groupNotifyUserIds() {
    if (!groupCall()) {
      return List.of(peerOf(callerUserId));
    }
    return invitedUserIds;
  }

  public boolean inviting() {
    return STATE_INVITING.equals(state);
  }

  public boolean active() {
    return STATE_ACTIVE.equals(state);
  }

  /** 已接通时长（秒）；未接通返回 0。 */
  public int durationSec(long nowMs) {
    if (answerAtMs <= 0) {
      return 0;
    }
    return (int) Math.max(0, (nowMs - answerAtMs) / 1000);
  }
}
