package com.im.cs.agent.service;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.cs.config.CsGrpcMetadata;
import com.im.cs.agent.dto.AgentConvListResponse;
import com.im.cs.agent.dto.CsConvItemResponse;
import com.im.cs.visitor.dao.entity.VisitorProfileEntity;
import com.im.cs.visitor.dao.mapper.VisitorProfileMapper;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
  private final VisitorProfileMapper visitorProfileMapper;
  private final CsVisitorPresenceService visitorPresenceService;

  public CsAgentService(
      @Qualifier("csConversationRpcBlockingStub") ConversationRpcGrpc.ConversationRpcBlockingStub conversationRpcStub,
      VisitorProfileMapper visitorProfileMapper,
      CsVisitorPresenceService visitorPresenceService) {
    this.conversationRpcStub = conversationRpcStub;
    this.visitorProfileMapper = visitorProfileMapper;
    this.visitorPresenceService = visitorPresenceService;
  }

  /**
   * 坐席认领会话（D31: open → assigned）。
   *
   * @param convId  要认领的会话 ID
   * @param agentId 当前坐席 user_id（来自 JwtAuthInterceptor）
   * @throws ImException CONFLICT 会话已被认领；NOT_FOUND 会话不存在
   */
  public void claimConv(long convId, long agentId) {
    ClaimCsConvResp resp = CsGrpcMetadata.withMetadata(conversationRpcStub).claimCsConv(
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
    ResolveCsConvResp resp = CsGrpcMetadata.withMetadata(conversationRpcStub).resolveCsConv(
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
    ListAgentCsConvsResp resp = CsGrpcMetadata.withMetadata(conversationRpcStub).listAgentCsConvs(
        ListAgentCsConvsReq.newBuilder()
            .setAgentId(agentId)
            .setLimit(limit > 0 ? limit : 20)
            .setOffset(offset)
            .build());
    long tenantId = TenantContext.requiredTenantId();
    List<CsConvItem> convs = resp.getConvsList();
    // 批量取访客资料 + 在线状态，避免逐条 N+1 查询。
    List<Long> visitorIds = convs.stream()
        .map(CsConvItem::getVisitorUserId)
        .filter(id -> id > 0)
        .distinct()
        .toList();
    Map<Long, VisitorProfileEntity> profiles = visitorIds.isEmpty()
        ? Map.of()
        : visitorProfileMapper.findByUserIds(tenantId, visitorIds).stream()
            .collect(Collectors.toMap(VisitorProfileEntity::getUserId, Function.identity(), (a, b) -> a));
    Map<Long, Boolean> presence = visitorPresenceService.onlineStatus(tenantId, visitorIds);
    List<CsConvItemResponse> items = convs.stream()
        .map(item -> toResponse(item,
            resolveVisitorName(item, profiles.get(item.getVisitorUserId())),
            presence.getOrDefault(item.getVisitorUserId(), false)))
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
    GetCsConvResp resp = CsGrpcMetadata.withMetadata(conversationRpcStub).getCsConv(
        GetCsConvReq.newBuilder().setConvId(convId).build());
    if (resp.getCode() != ErrorCode.OK.code()) {
      ErrorCode ec = ErrorCode.fromCode(resp.getCode()).orElse(ErrorCode.INTERNAL_ERROR);
      throw new ImException(ec, "查询 CS 会话失败 convId=" + convId);
    }
    long tenantId = TenantContext.requiredTenantId();
    CsConvItem item = resp.getConv();
    VisitorProfileEntity profile = item.getVisitorUserId() > 0
        ? visitorProfileMapper.findByUserId(tenantId, item.getVisitorUserId())
        : null;
    boolean online = visitorPresenceService.isOnline(tenantId, item.getVisitorUserId());
    return toResponse(item, resolveVisitorName(item, profile), online);
  }

  /** 访客显示名优先取 visitor_profile，缺失时回退 gRPC item 自带值。 */
  private String resolveVisitorName(CsConvItem item, VisitorProfileEntity profile) {
    if (profile != null && profile.getDisplayName() != null && !profile.getDisplayName().isBlank()) {
      return profile.getDisplayName();
    }
    return item.getVisitorName();
  }

  private CsConvItemResponse toResponse(CsConvItem item, String visitorName, boolean visitorOnline) {
    return new CsConvItemResponse(
        item.getConvId(),
        item.getCsStatus(),
        item.getAgentId(),
        item.getVisitorUserId(),
        visitorName,
        visitorOnline,
        item.getVisitorReadSeq(),
        item.getLastMsgTimeMs(),
        item.getLastMsgAbstract(),
        item.getMaxSeq());
  }
}
