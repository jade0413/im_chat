package com.im.message.service;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.grpc.GrpcMetadataKeys;
import com.im.common.tenant.TenantContext;
import com.im.common.trace.TraceContext;
import com.im.proto.body.ConvInfo;
import com.im.proto.body.MsgSend;
import com.im.proto.common.MsgContent;
import com.im.proto.common.NotificationContent;
import com.im.proto.rpc.ConnCtx;
import com.im.proto.rpc.ConversationRpcGrpc;
import com.im.proto.rpc.ResolveSystemConvReq;
import com.im.proto.rpc.ResolveSystemConvResp;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 系统通知落库（D40，T38）：把一条 {@link NotificationContent} 写入接收方 SYSTEM 会话，
 * 复用消息管道获得 seq / outbox / 多端同步 / 离线增量。
 *
 * <p>约定：
 * <ul>
 *   <li><b>系统发送方</b> sender_id = 0（保留为「系统」），前端按 conv_type=SYSTEM + event_type 渲染；</li>
 *   <li><b>幂等</b> client_msg_id = "sys:"+SHA-256(toUserId:eventType:payload)，含接收方，重发不重复落库（uk_client_msg）；</li>
 *   <li>不经 MessageSendService 校验链（其拒绝 NOTIFICATION），直接走 persist。</li>
 * </ul>
 */
@Service
public class SystemNotificationService {

  private static final String CALLER = "im-message-service";

  private final ConversationRpcGrpc.ConversationRpcBlockingStub conversationStub;
  private final MessagePersistService persistService;

  public SystemNotificationService(
      @Qualifier("conversationRpcBlockingStub")
      ConversationRpcGrpc.ConversationRpcBlockingStub conversationStub,
      MessagePersistService persistService) {
    this.conversationStub = conversationStub;
    this.persistService = persistService;
  }

  /**
   * @return 落库后的会话级 seq；若幂等命中（已存在）返回 0
   */
  public long send(long tenantId, long toUserId, String eventType, String payload) {
    if (toUserId <= 0 || eventType == null || eventType.isBlank()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "toUserId/eventType required");
    }
    String safePayload = payload == null ? "" : payload;
    ConvInfo conv = resolveSystemConv(toUserId);

    MsgContent content = MsgContent.newBuilder()
        .setNotification(NotificationContent.newBuilder()
            .setEventType(eventType)
            .setPayload(safePayload)
            .build())
        .build();
    MsgSend send = MsgSend.newBuilder()
        .setClientMsgId(clientMsgId(toUserId, eventType, safePayload))
        .setConvId(conv.getConvId())
        .setContent(content)
        .build();
    // 系统发送方：user_id = 0
    ConnCtx ctx = ConnCtx.newBuilder()
        .setTenantId(tenantId)
        .setUserId(0L)
        .build();
    try {
      return persistService.persist(tenantId, ctx, send, conv).seq();
    } catch (DuplicateKeyException ex) {
      // 幂等：同一通知已落库
      return 0L;
    }
  }

  private ConvInfo resolveSystemConv(long toUserId) {
    ResolveSystemConvResp resp = conversationStub
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata()))
        .resolveSystemConv(ResolveSystemConvReq.newBuilder().setUserId(toUserId).build());
    if (resp.getCode() != ErrorCode.OK.code()) {
      throw new ImException(ErrorCode.fromCode(resp.getCode()).orElse(ErrorCode.INTERNAL_ERROR));
    }
    if (!resp.hasConv() || resp.getConv().getConvId() <= 0) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "resolveSystemConv returned empty conv");
    }
    return resp.getConv();
  }

  /**
   * 幂等键：纳入接收方 toUserId（同一通知发给多个坐席如 cs.pending 不会被 uk_client_msg 误吞），
   * 用 SHA-256 稳定摘要（替代 32-bit hashCode），整体定长 ≤64。
   */
  private String clientMsgId(long toUserId, String eventType, String payload) {
    String digest = sha256Hex(toUserId + ":" + eventType + ":" + payload).substring(0, 40);
    return "sys:" + digest;  // 4 + 40 = 44 <= 64
  }

  private static String sha256Hex(String input) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256")
          .digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "SHA-256 unavailable", e);
    }
  }

  private Metadata metadata() {
    Metadata metadata = new Metadata();
    metadata.put(GrpcMetadataKeys.TENANT_ID, String.valueOf(TenantContext.requiredTenantId()));
    TraceContext.currentTraceId()
        .ifPresent(traceId -> metadata.put(GrpcMetadataKeys.TRACE_ID, traceId));
    metadata.put(GrpcMetadataKeys.CALLER, CALLER);
    return metadata;
  }
}
