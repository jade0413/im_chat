package com.im.conversation.service;

import com.im.common.grpc.GrpcMetadataKeys;
import com.im.common.tenant.TenantContext;
import com.im.common.trace.TraceContext;
import com.im.proto.body.Sender;
import com.im.proto.rpc.GetUsersReq;
import com.im.proto.rpc.GetUsersResp;
import com.im.proto.rpc.UserRpcGrpc;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * D-5：批量取用户公开资料（昵称/头像），用于会话列表填充 C2C 对端展示信息。
 *
 * <p>跨模块走 in-process gRPC（UserRpc.GetUsers）；tenant_id 经 metadata 透传，
 * 服务端拦截器注入 TenantContext 后按租户隔离查询。
 */
@Service
public class UserProfileClient {

  private final UserRpcGrpc.UserRpcBlockingStub userStub;

  public UserProfileClient(
      @Qualifier("conversationUserRpcBlockingStub") UserRpcGrpc.UserRpcBlockingStub userStub) {
    this.userStub = userStub;
  }

  /** 批量取 userId → 资料；空入参或无结果返回空 map（调用方按缺失降级）。 */
  public Map<Long, PeerProfile> batchGet(Collection<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return Map.of();
    }
    Set<Long> distinct = new LinkedHashSet<>();
    for (Long id : userIds) {
      if (id != null && id > 0) {
        distinct.add(id);
      }
    }
    if (distinct.isEmpty()) {
      return Map.of();
    }
    GetUsersResp resp = userStub
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata()))
        .getUsers(GetUsersReq.newBuilder().addAllUserIds(distinct).build());
    Map<Long, PeerProfile> result = new HashMap<>();
    for (Sender sender : resp.getUsersList()) {
      result.put(sender.getUserId(), new PeerProfile(sender.getNickname(), sender.getAvatar()));
    }
    return result;
  }

  private Metadata metadata() {
    Metadata metadata = new Metadata();
    metadata.put(GrpcMetadataKeys.TENANT_ID, String.valueOf(TenantContext.requiredTenantId()));
    TraceContext.currentTraceId()
        .ifPresent(traceId -> metadata.put(GrpcMetadataKeys.TRACE_ID, traceId));
    metadata.put(GrpcMetadataKeys.CALLER, "im-conversation-service");
    return metadata;
  }

  public record PeerProfile(String nickname, String avatar) {
  }
}
