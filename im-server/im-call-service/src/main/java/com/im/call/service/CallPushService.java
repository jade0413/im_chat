package com.im.call.service;

import com.im.common.grpc.GrpcMetadataKeys;
import com.im.common.tenant.TenantContext;
import com.im.common.trace.TraceContext;
import com.im.proto.body.CallNotify;
import com.im.proto.rpc.PushRpcGrpc;
import com.im.proto.rpc.PushToUsersReq;
import com.im.proto.rpc.PushToUsersResp;
import com.im.proto.ws.Cmd;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * CALL_NOTIFY 推送封装（经 push 模块 PushRpc，D5 铁律）。
 *
 * <p>need_ack=true：CALL_NOTIFY 是 MSG_PUSH 之外唯一的 need_ack 帧（协议 §3 修订，D45）——
 * 振铃/接通信令丢失不可接受，宁可网关断连触发客户端重连自愈。
 */
@Service
public class CallPushService {

  private final PushRpcGrpc.PushRpcBlockingStub pushStub;

  public CallPushService(
      @Qualifier("callPushRpcBlockingStub") PushRpcGrpc.PushRpcBlockingStub pushStub) {
    this.pushStub = pushStub;
  }

  /** 推给目标用户全端；返回在线连接数（0 = 全端离线）。 */
  public int notifyUsers(Collection<Long> userIds, CallNotify notify) {
    return notifyUsers(userIds, notify, 0L, "");
  }

  /** excludeConnId 用于"被叫其他端停铃"等排除发起连接的场景。 */
  public int notifyUsers(
      Collection<Long> userIds, CallNotify notify, long excludeUserId, String excludeConnId) {
    PushToUsersReq request = PushToUsersReq.newBuilder()
        .addAllUserIds(userIds)
        .setCmd(Cmd.CALL_NOTIFY_VALUE)
        .setBody(notify.toByteString())
        .setNeedAck(true)
        .setExcludeUserId(excludeUserId)
        .setExcludeConnId(excludeConnId == null ? "" : excludeConnId)
        .build();
    PushToUsersResp resp = pushStub
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata()))
        .pushToUsers(request);
    return resp.getOnlineCount();
  }

  private Metadata metadata() {
    Metadata metadata = new Metadata();
    metadata.put(GrpcMetadataKeys.TENANT_ID, String.valueOf(TenantContext.requiredTenantId()));
    TraceContext.currentTraceId()
        .ifPresent(traceId -> metadata.put(GrpcMetadataKeys.TRACE_ID, traceId));
    metadata.put(GrpcMetadataKeys.CALLER, "im-call-service");
    return metadata;
  }
}
