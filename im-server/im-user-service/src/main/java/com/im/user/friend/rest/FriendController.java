package com.im.user.friend.rest;

import com.im.common.auth.UserContext;
import com.im.common.web.ApiResponse;
import com.im.user.friend.FriendService;
import com.im.user.friend.dto.FriendItemResponse;
import com.im.user.friend.dto.FriendRequestItemResponse;
import com.im.user.friend.dto.SendFriendRequestRequest;
import com.im.user.friend.dto.SendFriendRequestResponse;
import com.im.user.friend.dto.UpdateFriendSettingsRequest;
import com.im.user.friend.dto.UpdateRemarkRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 好友申请 / 关系（D40）。详见 docs/friend-service-design.md §6.1。 */
@RestController
@RequestMapping("/api/v1/friend")
public class FriendController {

  private final FriendService friendService;

  public FriendController(FriendService friendService) {
    this.friendService = friendService;
  }

  /** 发起好友申请。 */
  @PostMapping("/requests")
  public ApiResponse<SendFriendRequestResponse> send(
      @Valid @RequestBody SendFriendRequestRequest request) {
    return ApiResponse.ok(
        friendService.sendRequest(UserContext.requiredUserId(), request.toUserId(), request.note()));
  }

  /** 同意申请（仅接收方）。 */
  @PostMapping("/requests/{id}/accept")
  public ApiResponse<Void> accept(@PathVariable long id) {
    friendService.accept(id, UserContext.requiredUserId());
    return ApiResponse.ok(null);
  }

  /** 拒绝申请。 */
  @PostMapping("/requests/{id}/reject")
  public ApiResponse<Void> reject(@PathVariable long id) {
    friendService.reject(id, UserContext.requiredUserId());
    return ApiResponse.ok(null);
  }

  /** 忽略申请。 */
  @PostMapping("/requests/{id}/ignore")
  public ApiResponse<Void> ignore(@PathVariable long id) {
    friendService.ignore(id, UserContext.requiredUserId());
    return ApiResponse.ok(null);
  }

  /** 申请历史（通知页）。role=incoming(默认)/outgoing。 */
  @GetMapping("/requests")
  public ApiResponse<List<FriendRequestItemResponse>> listRequests(
      @RequestParam(defaultValue = "incoming") String role,
      @RequestParam(defaultValue = "50") int limit) {
    return ApiResponse.ok(friendService.listRequests(UserContext.requiredUserId(), role, limit));
  }

  /** 好友列表。 */
  @GetMapping("/list")
  public ApiResponse<List<FriendItemResponse>> listFriends() {
    return ApiResponse.ok(friendService.listFriends(UserContext.requiredUserId()));
  }

  /** 删除好友（双向）。 */
  @DeleteMapping("/{friendId}")
  public ApiResponse<Void> delete(@PathVariable long friendId) {
    friendService.deleteFriend(UserContext.requiredUserId(), friendId);
    return ApiResponse.ok(null);
  }

  /** 修改好友备注名。 */
  @PutMapping("/{friendId}/remark")
  public ApiResponse<Void> remark(@PathVariable long friendId,
      @Valid @RequestBody UpdateRemarkRequest request) {
    friendService.updateRemark(UserContext.requiredUserId(), friendId, request.remark());
    return ApiResponse.ok(null);
  }

  /** 好友设置：加我是否需要验证。 */
  @PutMapping("/settings")
  public ApiResponse<Void> settings(@Valid @RequestBody UpdateFriendSettingsRequest request) {
    friendService.updateVerifySetting(UserContext.requiredUserId(), request.friendVerifyRequired());
    return ApiResponse.ok(null);
  }
}
