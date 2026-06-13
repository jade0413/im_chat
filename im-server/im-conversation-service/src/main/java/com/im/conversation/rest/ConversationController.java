package com.im.conversation.rest;

import com.im.common.auth.UserContext;
import com.im.common.web.ApiResponse;
import com.im.conversation.service.ConversationService;
import com.im.proto.body.ConvInfo;
import com.im.proto.rpc.ResolveConvReq;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话管理 REST 接口（前端发起）。
 *
 * <p>D17：开放式单聊，任意用户可主动建立 C2C 会话，后端幂等（相同双方 c2cKey 只创建一次）。
 */
@RestController
@RequestMapping("/api/v1/convs")
public class ConversationController {

  private final ConversationService conversationService;

  public ConversationController(ConversationService conversationService) {
    this.conversationService = conversationService;
  }

  /**
   * 打开或创建与指定用户的单聊会话，返回会话基本信息。
   *
   * <pre>POST /api/v1/convs/c2c?toUserId=1234</pre>
   *
   * <p>幂等：相同双方多次调用返回相同 convId，可直接用于跳转聊天界面。
   */
  @PostMapping("/c2c")
  public ApiResponse<ConvInfoResponse> openC2c(
      @RequestParam @Positive long toUserId) {
    long selfUserId = UserContext.requiredUserId();
    ResolveConvReq req = ResolveConvReq.newBuilder()
        .setFromUserId(selfUserId)
        .setToUserId(toUserId)
        .build();
    ConvInfo conv = conversationService.resolve(req);
    return ApiResponse.ok(toResponse(conv));
  }

  private ConvInfoResponse toResponse(ConvInfo conv) {
    return new ConvInfoResponse(
        conv.getConvId(),
        conv.getType().getNumber(),
        conv.getTitle(),
        conv.getAvatar().isBlank() ? null : conv.getAvatar(),
        conv.getPeerUserId() > 0 ? conv.getPeerUserId() : null,
        conv.getGroupId() > 0 ? conv.getGroupId() : null,
        conv.getMaxSeq(),
        conv.getReadSeq());
  }

  /** 会话基本信息响应（不含消息体）。 */
  public record ConvInfoResponse(
      long convId,
      int type,
      String title,
      String avatar,
      Long peerUserId,
      Long groupId,
      long maxSeq,
      long readSeq) {}
}
