package com.im.conversation.service;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.grpc.GrpcMetadataKeys;
import com.im.common.tenant.TenantContext;
import com.im.common.trace.TraceContext;
import com.im.proto.rpc.CheckRelationReq;
import com.im.proto.rpc.CheckRelationResp;
import com.im.proto.rpc.UserRpcGrpc;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 打开/创建 C2C 会话前的关系校验，与消息发送链路（im-message-service）保持同一语义：
 * 被对端拉黑 → BLOCKED_BY_PEER；租户开启好友制且非好友 → FRIEND_REQUIRED。
 *
 * <p>修复：此前 openC2c 直接建会话不校验关系，导致好友制下仍能开聊、发了才失败。
 */
@Service
public class ConversationRelationGuard {

  private final UserRpcGrpc.UserRpcBlockingStub userStub;

  public ConversationRelationGuard(
      @Qualifier("conversationUserRpcBlockingStub") UserRpcGrpc.UserRpcBlockingStub userStub) {
    this.userStub = userStub;
  }

  public void ensureCanOpenC2c(long fromUserId, long toUserId) {
    if (fromUserId <= 0 || toUserId <= 0) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "from_user_id and to_user_id must be positive");
    }
    CheckRelationResp response = userStub
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata()))
        .checkRelation(CheckRelationReq.newBuilder()
            .setFromUserId(fromUserId)
            .setToUserId(toUserId)
            .build());
    if (response.getBlocked()) {
      throw new ImException(ErrorCode.BLOCKED_BY_PEER);
    }
    if (response.getFriendRequiredUnmet()) {
      throw new ImException(ErrorCode.FRIEND_REQUIRED);
    }
  }

  private Metadata metadata() {
    Metadata metadata = new Metadata();
    metadata.put(GrpcMetadataKeys.TENANT_ID, String.valueOf(TenantContext.requiredTenantId()));
    TraceContext.currentTraceId().ifPresent(traceId -> metadata.put(GrpcMetadataKeys.TRACE_ID, traceId));
    metadata.put(GrpcMetadataKeys.CALLER, "im-conversation-service");
    return metadata;
  }
}
