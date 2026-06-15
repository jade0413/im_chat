package com.im.user.friend;

import com.im.common.grpc.GrpcMetadataKeys;
import com.im.common.tenant.TenantContext;
import com.im.common.trace.TraceContext;
import com.im.proto.rpc.MessageRpcGrpc;
import com.im.proto.rpc.SendSystemNotificationReq;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 好友系统通知 gRPC 实现（D40，T38）：调 message 模块 {@code SendSystemNotification}，
 * 把通知写进接收方 SYSTEM 会话（拿 seq / 多端同步 / 离线增量）。
 *
 * <p>标 {@link Primary} 覆盖占位的 {@link LoggingFriendNotificationPort}。
 * 失败仅记日志、不抛出——状态唯一真相在 friend_request 表，客户端可经
 * {@code GET /api/v1/friend/requests} 拉取兜底，通知失败不影响好友关系正确性。
 */
@Component
@Primary
public class GrpcFriendNotificationPort implements FriendNotificationPort {

  private static final Logger log = LoggerFactory.getLogger(GrpcFriendNotificationPort.class);
  private static final String CALLER = "im-user-service";

  private final MessageRpcGrpc.MessageRpcBlockingStub messageStub;

  public GrpcFriendNotificationPort(
      @Qualifier("userMessageRpcBlockingStub")
      MessageRpcGrpc.MessageRpcBlockingStub messageStub) {
    this.messageStub = messageStub;
  }

  @Override
  public void send(long tenantId, long toUserId, String eventType, String payloadJson) {
    try {
      messageStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata(tenantId)))
          .sendSystemNotification(SendSystemNotificationReq.newBuilder()
              .setTenantId(tenantId)
              .setToUserId(toUserId)
              .setEventType(eventType)
              .setPayload(payloadJson == null ? "" : payloadJson)
              .build());
    } catch (Exception ex) {
      log.warn("[friend-notify] send failed tenant={} to={} event={}: {}",
          tenantId, toUserId, eventType, ex.toString());
    }
  }

  private Metadata metadata(long tenantId) {
    Metadata metadata = new Metadata();
    metadata.put(GrpcMetadataKeys.TENANT_ID, String.valueOf(tenantId));
    TraceContext.currentTraceId()
        .ifPresent(traceId -> metadata.put(GrpcMetadataKeys.TRACE_ID, traceId));
    metadata.put(GrpcMetadataKeys.CALLER, CALLER);
    return metadata;
  }
}
