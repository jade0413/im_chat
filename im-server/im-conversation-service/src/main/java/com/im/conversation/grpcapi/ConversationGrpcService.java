package com.im.conversation.grpcapi;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.conversation.dao.entity.ConversationEntity;
import com.im.conversation.service.ConversationListResult;
import com.im.conversation.service.ConversationService;
import com.im.conversation.service.CsConversationService;
import com.im.conversation.service.CsConversationService.FindOrCreateResult;
import com.im.proto.body.ConvInfo;
import com.im.proto.rpc.ClaimCsConvReq;
import com.im.proto.rpc.ClaimCsConvResp;
import com.im.proto.rpc.ConversationRpcGrpc;
import com.im.proto.rpc.CsConvItem;
import com.im.proto.rpc.FindOrCreateCsConvReq;
import com.im.proto.rpc.FindOrCreateCsConvResp;
import com.im.proto.rpc.GetCsConvReq;
import com.im.proto.rpc.GetCsConvResp;
import com.im.proto.rpc.GetMembersReq;
import com.im.proto.rpc.GetMembersResp;
import com.im.proto.rpc.GetMemberConvReq;
import com.im.proto.rpc.GetMemberConvResp;
import com.im.proto.rpc.ListAgentCsConvsReq;
import com.im.proto.rpc.ListAgentCsConvsResp;
import com.im.proto.rpc.ListMemberConvsReq;
import com.im.proto.rpc.ListMemberConvsResp;
import com.im.proto.rpc.ResolveCsConvReq;
import com.im.proto.rpc.ResolveCsConvResp;
import com.im.proto.rpc.ResolveConvReq;
import com.im.proto.rpc.ResolveConvResp;
import io.grpc.stub.StreamObserver;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConversationGrpcService extends ConversationRpcGrpc.ConversationRpcImplBase {

  private final ConversationService conversationService;
  private final CsConversationService csConversationService;

  public ConversationGrpcService(ConversationService conversationService,
      CsConversationService csConversationService) {
    this.conversationService = conversationService;
    this.csConversationService = csConversationService;
  }

  @Override
  public void resolveConv(ResolveConvReq request, StreamObserver<ResolveConvResp> responseObserver) {
    ResolveConvResp response;
    try {
      ConvInfo conv = conversationService.resolve(request);
      response = ResolveConvResp.newBuilder()
          .setCode(ErrorCode.OK.code())
          .setConv(conv)
          .build();
    } catch (ImException ex) {
      response = ResolveConvResp.newBuilder()
          .setCode(ex.errorCode().code())
          .build();
    } catch (Exception ex) {
      response = ResolveConvResp.newBuilder()
          .setCode(ErrorCode.INTERNAL_ERROR.code())
          .build();
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getMembers(GetMembersReq request, StreamObserver<GetMembersResp> responseObserver) {
    GetMembersResp response;
    try {
      ConversationService.MembersResult result = conversationService.getMembersResult(request.getConvId());
      GetMembersResp.Builder builder = GetMembersResp.newBuilder()
          .addAllUserIds(result.userIds())
          .setConvType(result.convType())
          .setCsStatus(result.csStatus())
          .setAgentId(result.agentId());
      response = builder.build();
    } catch (Exception ex) {
      response = GetMembersResp.getDefaultInstance();
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getMemberConv(GetMemberConvReq request,
      StreamObserver<GetMemberConvResp> responseObserver) {
    GetMemberConvResp response;
    try {
      response = GetMemberConvResp.newBuilder()
          .setCode(ErrorCode.OK.code())
          .setConv(conversationService.getMemberConv(request.getUserId(), request.getConvId()))
          .build();
    } catch (ImException ex) {
      response = GetMemberConvResp.newBuilder()
          .setCode(ex.errorCode().code())
          .build();
    } catch (Exception ex) {
      response = GetMemberConvResp.newBuilder()
          .setCode(ErrorCode.INTERNAL_ERROR.code())
          .build();
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void listMemberConvs(ListMemberConvsReq request,
      StreamObserver<ListMemberConvsResp> responseObserver) {
    ListMemberConvsResp response;
    try {
      ConversationListResult result = conversationService.listMemberConvs(
          request.getUserId(), request.getLimit(), request.getConvListVersion());
      response = ListMemberConvsResp.newBuilder()
          .addAllConvs(result.convs())
          .setHasMore(result.hasMore())
          .setConvListVersion(result.convListVersion())
          .build();
    } catch (Exception ex) {
      response = ListMemberConvsResp.getDefaultInstance();
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  /** 查找或创建访客的 CS 会话（T31）。 */
  @Override
  public void findOrCreateCsConv(FindOrCreateCsConvReq request,
      StreamObserver<FindOrCreateCsConvResp> responseObserver) {
    FindOrCreateCsConvResp response;
    try {
      FindOrCreateResult result = csConversationService.findOrCreateCsConv(
          request.getTenantId(), request.getVisitorUserId());
      response = FindOrCreateCsConvResp.newBuilder()
          .setCode(ErrorCode.OK.code())
          .setConvId(result.convId())
          .setIsNew(result.isNew())
          .setCsStatus(result.csStatus())
          .build();
    } catch (Exception ex) {
      response = FindOrCreateCsConvResp.newBuilder()
          .setCode(ErrorCode.INTERNAL_ERROR.code())
          .build();
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  /** 坐席认领 open 会话 → assigned（D31）。 */
  @Override
  public void claimCsConv(ClaimCsConvReq request, StreamObserver<ClaimCsConvResp> responseObserver) {
    ClaimCsConvResp response;
    try {
      csConversationService.claimConv(
          com.im.common.tenant.TenantContext.requiredTenantId(),
          request.getConvId(), request.getAgentId());
      response = ClaimCsConvResp.newBuilder().setCode(ErrorCode.OK.code()).build();
    } catch (ImException ex) {
      response = ClaimCsConvResp.newBuilder().setCode(ex.errorCode().code()).build();
    } catch (Exception ex) {
      response = ClaimCsConvResp.newBuilder().setCode(ErrorCode.INTERNAL_ERROR.code()).build();
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  /** 坐席结单 assigned → resolved（D31）。 */
  @Override
  public void resolveCsConv(ResolveCsConvReq request, StreamObserver<ResolveCsConvResp> responseObserver) {
    ResolveCsConvResp response;
    try {
      csConversationService.resolveConv(
          com.im.common.tenant.TenantContext.requiredTenantId(),
          request.getConvId(), request.getAgentId());
      response = ResolveCsConvResp.newBuilder().setCode(ErrorCode.OK.code()).build();
    } catch (ImException ex) {
      response = ResolveCsConvResp.newBuilder().setCode(ex.errorCode().code()).build();
    } catch (Exception ex) {
      response = ResolveCsConvResp.newBuilder().setCode(ErrorCode.INTERNAL_ERROR.code()).build();
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  /** 坐席工作台会话列表（D33）。 */
  @Override
  public void listAgentCsConvs(ListAgentCsConvsReq request,
      StreamObserver<ListAgentCsConvsResp> responseObserver) {
    ListAgentCsConvsResp response;
    try {
      long tenantId = com.im.common.tenant.TenantContext.requiredTenantId();
      int limit = request.getLimit() > 0 ? request.getLimit() : 20;
      List<ConversationEntity> convs = csConversationService.listAgentCsConvs(
          tenantId, request.getAgentId(), limit, request.getOffset());
      List<CsConvItem> items = convs.stream().map(this::toCsConvItem).toList();
      response = ListAgentCsConvsResp.newBuilder()
          .addAllConvs(items)
          .setHasMore(convs.size() == limit)
          .build();
    } catch (Exception ex) {
      response = ListAgentCsConvsResp.getDefaultInstance();
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  /** 取单个 CS 会话元数据（坐席侧）。 */
  @Override
  public void getCsConv(GetCsConvReq request, StreamObserver<GetCsConvResp> responseObserver) {
    GetCsConvResp response;
    try {
      long tenantId = com.im.common.tenant.TenantContext.requiredTenantId();
      ConversationEntity conv = csConversationService.getCsConv(tenantId, request.getConvId());
      response = GetCsConvResp.newBuilder()
          .setCode(ErrorCode.OK.code())
          .setConv(toCsConvItem(conv))
          .build();
    } catch (ImException ex) {
      response = GetCsConvResp.newBuilder().setCode(ex.errorCode().code()).build();
    } catch (Exception ex) {
      response = GetCsConvResp.newBuilder().setCode(ErrorCode.INTERNAL_ERROR.code()).build();
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  private CsConvItem toCsConvItem(ConversationEntity conv) {
    CsConvItem.Builder b = CsConvItem.newBuilder()
        .setConvId(conv.getId())
        .setCsStatus(conv.getCsStatus() != null ? conv.getCsStatus() : 0)
        .setAgentId(conv.getAgentId() != null ? conv.getAgentId() : 0L)
        .setMaxSeq(conv.getMaxSeq() != null ? conv.getMaxSeq() : 0L)
        .setLastMsgAbstract(conv.getLastMsgAbstract() != null ? conv.getLastMsgAbstract() : "");
    if (conv.getLastMsgTime() != null) {
      b.setLastMsgTimeMs(conv.getLastMsgTime().toInstant(ZoneOffset.UTC).toEpochMilli());
    }
    // visitor_user_id / visitor_name 由调用方按需补充（CS service 查 visitor_profile）
    return b.build();
  }
}
