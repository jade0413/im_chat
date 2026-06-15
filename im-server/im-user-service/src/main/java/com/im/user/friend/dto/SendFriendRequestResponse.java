package com.im.user.friend.dto;

/**
 * 发起申请结果。
 * <ul>
 *   <li>{@code pending} — 已发出，等待对方验证；</li>
 *   <li>{@code accepted} — 对方免验证，已直接成为好友；</li>
 *   <li>{@code already_friend} — 已是好友，未重复建申请；</li>
 *   <li>{@code ok} — 受理成功（含被对方拉黑的静默情形，不泄露）。</li>
 * </ul>
 * requestId 在 pending/accepted 时返回，其余可为 null。
 */
public record SendFriendRequestResponse(String result, Long requestId) {

  public static SendFriendRequestResponse pending(long id) {
    return new SendFriendRequestResponse("pending", id);
  }

  public static SendFriendRequestResponse accepted(long id) {
    return new SendFriendRequestResponse("accepted", id);
  }

  public static SendFriendRequestResponse alreadyFriend() {
    return new SendFriendRequestResponse("already_friend", null);
  }

  public static SendFriendRequestResponse ok() {
    return new SendFriendRequestResponse("ok", null);
  }
}
