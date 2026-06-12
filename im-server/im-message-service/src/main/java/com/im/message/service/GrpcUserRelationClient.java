package com.im.message.service;

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
import org.springframework.stereotype.Service;

@Service
public class GrpcUserRelationClient implements UserRelationClient {

  private final UserRpcGrpc.UserRpcBlockingStub userStub;

  public GrpcUserRelationClient(UserRpcGrpc.UserRpcBlockingStub userStub) {
    this.userStub = userStub;
  }

  @Override
  public void ensureCanSendC2c(long fromUserId, long toUserId) {
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
    metadata.put(GrpcMetadataKeys.CALLER, "im-message-service");
    return metadata;
  }
}
