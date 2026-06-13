package com.im.push.service;

import com.im.common.grpc.GrpcMetadataKeys;
import com.im.common.tenant.TenantContext;
import com.im.common.trace.TraceContext;
import com.im.proto.rpc.GetOnlineAgentIdsReq;
import com.im.proto.rpc.UserRpcGrpc;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 在线坐席列表客户端（D33）。
 *
 * <p>通过 gRPC 向 user-service 查询当前租户下所有 online/busy 坐席的 user_id。
 * 用于 CS open 会话消息推送扇出。
 */
@Service
public class OnlineAgentClient {

  private final UserRpcGrpc.UserRpcBlockingStub userRpcStub;

  public OnlineAgentClient(
      @Qualifier("pushUserRpcBlockingStub")
      UserRpcGrpc.UserRpcBlockingStub pushUserRpcBlockingStub) {
    this.userRpcStub = pushUserRpcBlockingStub;
  }

  /**
   * 返回当前租户所有 online/busy 坐席的 user_id。
   * 若返回空列表，推送侧不补充坐席接收方（open conv 有消息但无坐席在线属正常离线留言场景）。
   */
  public List<Long> getOnlineAgentIds() {
    long tenantId = TenantContext.requiredTenantId();
    Metadata metadata = new Metadata();
    metadata.put(GrpcMetadataKeys.TENANT_ID, String.valueOf(tenantId));
    TraceContext.currentTraceId().ifPresent(traceId -> metadata.put(GrpcMetadataKeys.TRACE_ID, traceId));
    metadata.put(GrpcMetadataKeys.CALLER, "im-push-service");

    return userRpcStub
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
        .getOnlineAgentIds(GetOnlineAgentIdsReq.newBuilder().setTenantId(tenantId).build())
        .getAgentIdsList();
  }
}
