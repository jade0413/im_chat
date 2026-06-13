package com.im.cs.agent.service;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.cs.agent.dto.AgentConvListResponse;
import com.im.cs.agent.dto.CsConvItemResponse;
import com.im.proto.rpc.ClaimCsConvReq;
import com.im.proto.rpc.ClaimCsConvResp;
import com.im.proto.rpc.ConversationRpcGrpc;
import com.im.proto.rpc.CsConvItem;
import com.im.proto.rpc.GetCsConvReq;
import com.im.proto.rpc.GetCsConvResp;
import com.im.proto.rpc.ListAgentCsConvsReq;
import com.im.proto.rpc.ListAgentCsConvsResp;
import com.im.proto.rpc.ResolveCsConvReq;
import com.im.proto.rpc.ResolveCsConvResp;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * CS 坐席操作服务（T35）。
 *
 * <p>通过 gRPC 调用 conversation-service 完成会话状态变更（claim / resolve）
 * 以及工作台列表查询，遵守模块隔离铁律（D5）。
 */
@Service
public class CsAgentService {

  private final ConversationRpcGrpc.ConversationRpcBlockingStub conversationRpcStub;

  public CsAgentService(
      @Qualifier("csConversationRpcBlockingStub")
      ConversationRpcGrpc.ConversationRpcBlockingStub conversationRpcStub) {
    this.conversationRpcStub = conversationRpcStub;
  }

  /**
   * 坐席认领会话（D31: open → assigned）。
   *
   * @param convId  要认领的会话 ID
   * @param agentId 当前坐席 user_id（来自 JwtAuthInterceptor）
   * @throws ImException CONFLICT 会话已被认领；NOT_FOUND 会话不存在
   */
  public void claimConv(long convId, long agentId) {
    ClaimCsConvResp resp = conversationRpcStub.claimCsConv(
        ClaimCsConvReq.newBuilder()
            .setConvId(convId)
            .setAgentId(agentId)
            .build());
    if (resp.getCode() != ErrorCode.OK.code()) {
      ErrorCode ec = ErrorCode.fromCode(resp.getCode()).orElse(ErrorCode.INTERNAL_ERROR);
      throw new ImException(ec, "认领会话失败 code=" + resp.getCode());
    }
  }

  /**
   * 坐席结单（D31: assigned → resolved）。
   *
   * @param convId  要结单的会话 ID
   * @param agentId 当前坐席 user_id
   * @throws ImException FORBIDDEN 不是绑定坐席；NOT_FOUND 会话不存在
   */
  public void resolveConv(long convId, long agentId) {
    ResolveCsConvResp resp = conversationRpcStub.resolveCsConv(
        ResolveCsConvReq.newBuilder()
            .setConvId(convId)
            .setAgentId(agentId)
            .build());
    if (resp.getCode() != ErrorCode.OK.code()) {
      ErrorCode ec = ErrorCode.fromCode(resp.getCode()).orElse(ErrorCode.INTERNAL_ERROR);
      throw new ImException(ec, "结单失败 code=" + resp.getCode());
    }
  }

  /**
   * 坐席工作台列表（D33）：open 全部 + 本人 assigned。
   *
   * @param agentId 当前坐席 user_id
   * @param limit   分页大小（默认 20）
   * @param offset  分页偏移
   */
  public AgentConvListResponse listConvs(long agentId, int limit, int offset) {
    ListAgentCsConvsResp resp = conversationRpcStub.listAgentCsConvs(
        ListAgentCsConvsReq.newBuilder()
            .setAgentId(agentId)
            .setLimit(limit > 0 ? limit : 20)
            .setOffset(offset)
            .build());
    List<CsConvItemResponse> items = resp.getConvsList().stream()
        .map(this::toResponse)
        .toList();
    return new AgentConvListResponse(items, resp.getHasMore());
  }

  /**
   * 查询单个 CS 会话元数据（坐席查看访客详情）。
   *
   * @param convId  会话 ID
   * @param agentId 当前坐席 user_id（用于日志，不做权限拦截——open 会话任何坐席可查）
   */
  public CsConvItemResponse getConv(long convId, long agentId) {
    GetCsConvResp resp = conversationRpcStub.getCsConv(
        GetCsConvReq.newBuilder().setConvId(convId).build());
    if (resp.getCode() != ErrorCode.OK.code()) {
      ErrorCode ec = ErrorCode.fromCode(resp.getCode()).orElse(ErrorCode.INTERNAL_ERROR);
      throw new ImException(ec, "查询 CS 会话失败 convId=" + convId);
    }
    return toResponse(resp.getConv());
  }

  private CsConvItemResponse toResponse(CsConvItem item) {
    return new CsConvItemResponse(
        item.getConvId(),
        item.getCsStatus(),
        item.getAgentId(),
        item.getVisitorUserId(),
        item.getVisitorName(),
        item.getLastMsgTimeMs(),
        item.getLastMsgAbstract(),
        item.getMaxSeq());
  }
}
