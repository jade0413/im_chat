package com.im.call.service;

/** Redis 呼叫会话快照。state：INVITING / ACTIVE（ENDED 即键删除）。 */
public record CallSession(
    String callId,
    long callerUserId,
    long calleeUserId,
    int media,
    String state,
    String clientCallId,
    long inviteAtMs,
    long answerAtMs
) {

  public static final String STATE_INVITING = "INVITING";
  public static final String STATE_ACTIVE = "ACTIVE";

  public boolean isParticipant(long userId) {
    return userId == callerUserId || userId == calleeUserId;
  }

  public long peerOf(long userId) {
    return userId == callerUserId ? calleeUserId : callerUserId;
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
