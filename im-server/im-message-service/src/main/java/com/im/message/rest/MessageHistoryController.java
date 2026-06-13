package com.im.message.rest;

import com.im.common.auth.UserContext;
import com.im.common.web.ApiResponse;
import com.im.message.dto.MessageHistoryResponse;
import com.im.message.dto.MessageItemResponse;
import com.im.message.service.MessagePage;
import com.im.message.service.MessageQueryService;
import com.im.message.service.MessageRevokeService;
import com.im.proto.body.MsgPush;
import com.im.proto.common.MsgContent;
import com.im.proto.common.MsgStatus;
import com.im.proto.common.RevokeReason;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/convs")
public class MessageHistoryController {

  private final MessageQueryService messageQueryService;
  private final MessageRevokeService messageRevokeService;

  public MessageHistoryController(MessageQueryService messageQueryService,
      MessageRevokeService messageRevokeService) {
    this.messageQueryService = messageQueryService;
    this.messageRevokeService = messageRevokeService;
  }

  @GetMapping("/{convId}/messages")
  public ApiResponse<MessageHistoryResponse> history(
      @PathVariable long convId,
      @RequestParam(name = "end_seq", required = false) Long endSeq,
      @RequestParam(name = "limit", defaultValue = "20") int limit) {
    MessagePage page = messageQueryService.history(UserContext.requiredUserId(), convId, endSeq, limit);
    return ApiResponse.ok(new MessageHistoryResponse(
        convId,
        page.readSeq(),
        page.messages().stream().map(this::toResponse).toList(),
        page.hasMore()));
  }

  @PostMapping("/{convId}/messages/{seq}/revoke")
  public ApiResponse<Void> revoke(
      @PathVariable long convId,
      @PathVariable long seq) {
    messageRevokeService.revoke(convId, seq, RevokeReason.BY_SENDER, UserContext.requiredUserId());
    return ApiResponse.ok(null);
  }

  /**
   * B7 修复：用 protobuf oneof ContentCase 替代 ext map 字符串解析，消除运行时约定耦合。
   */
  private MessageItemResponse toResponse(MsgPush push) {
    MsgContent content = push.getContent();
    int msgType = switch (content.getContentCase()) {
      case TEXT -> 1;
      case IMAGE -> 2;
      case VOICE -> 3;
      case FILE -> 4;
      case CUSTOM -> 10;
      default -> 0;
    };
    int status = intExt(push, "status", MsgStatus.NORMAL.getNumber());
    int revokeReason = intExt(push, "revoke_reason", RevokeReason.REVOKE_REASON_UNSPECIFIED.getNumber());
    String text = content.hasText() ? content.getText().getText() : "";
    return new MessageItemResponse(
        push.getConvId(),
        push.getSeq(),
        push.getServerMsgId(),
        push.getClientMsgId(),
        push.getSender().getUserId(),
        push.getSendTime(),
        msgType,
        status,
        revokeReason,
        text);
  }

  private int intExt(MsgPush push, String key, int fallback) {
    String value = push.getExtOrDefault(key, "");
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }
}
