package com.im.conversation.service;

import com.im.common.grpc.GrpcMetadataKeys;
import com.im.common.tenant.TenantContext;
import com.im.common.trace.TraceContext;
import com.im.proto.body.ReadNotify;
import com.im.proto.rpc.ConnCtx;
import com.im.proto.rpc.PushRpcGrpc;
import com.im.proto.rpc.PushToUsersReq;
import com.im.proto.ws.Cmd;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class GrpcReadReceiptPusher implements ReadReceiptPusher {

  private final PushRpcGrpc.PushRpcBlockingStub pushStub;

  public GrpcReadReceiptPusher(
      @Qualifier("conversationPushRpcBlockingStub") PushRpcGrpc.PushRpcBlockingStub pushStub) {
    this.pushStub = pushStub;
  }

  @Override
  public void pushReadNotify(ConnCtx ctx, Collection<Long> targetUserIds, ReadNotify notify) {
    if (targetUserIds == null || targetUserIds.isEmpty()) {
      return;
    }
    PushToUsersReq request = PushToUsersReq.newBuilder()
        .addAllUserIds(targetUserIds)
        .setCmd(Cmd.READ_NOTIFY_VALUE)
        .setBody(notify.toByteString())
        .setNeedAck(false)
        .setExcludeUserId(ctx.getUserId())
        .setExcludeConnId(ctx.getConnId())
        .build();
    pushStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata()))
        .pushToUsers(request);
  }

  private Metadata metadata() {
    Metadata metadata = new Metadata();
    metadata.put(GrpcMetadataKeys.TENANT_ID, String.valueOf(TenantContext.requiredTenantId()));
    TraceContext.currentTraceId().ifPresent(traceId -> metadata.put(GrpcMetadataKeys.TRACE_ID, traceId));
    metadata.put(GrpcMetadataKeys.CALLER, "im-conversation-service");
    return metadata;
  }
}
