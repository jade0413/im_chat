package com.im.message.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.message.dao.entity.MessageEntity;
import com.im.message.dao.mapper.ConversationProgressMapper;
import com.im.message.dao.mapper.MessageMapper;
import com.im.proto.body.ConvInfo;
import com.im.proto.body.MsgPush;
import com.im.proto.body.SyncReq;
import com.im.proto.body.SyncResp;
import com.im.proto.common.ConvType;
import com.im.proto.rpc.PullMsgsReq;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MessageQueryService {

  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;
  private static final int MAX_SYNC_CONV_VERSIONS = 500;

  private final MessageMapper messageMapper;
  private final ConversationProgressMapper conversationProgressMapper;
  private final MessageAssembler assembler;
  private final ConversationMemberClient memberClient;

  public MessageQueryService(MessageMapper messageMapper,
      ConversationProgressMapper conversationProgressMapper,
      MessageAssembler assembler,
      ConversationMemberClient memberClient) {
    this.messageMapper = messageMapper;
    this.conversationProgressMapper = conversationProgressMapper;
    this.assembler = assembler;
    this.memberClient = memberClient;
  }

  public SyncResp sync(long userId, SyncReq request) {
    TenantContext.requiredTenantId();
    if (request.getConvVersionsCount() > MAX_SYNC_CONV_VERSIONS) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "too many conversation versions in one sync request");
    }
    ConversationListPage convList = memberClient.listMemberConvs(userId, request.getConvListVersion());
    SyncResp.Builder response = SyncResp.newBuilder()
        .setFullSync(convList.hasMore())
        .setConvListVersion(convList.convListVersion());

    Map<Long, Long> localMaxSeqByConv = new HashMap<>();
    for (SyncReq.ConvVersion version : request.getConvVersionsList()) {
      localMaxSeqByConv.put(version.getConvId(), version.getLocalMaxSeq());
    }
    Set<Long> changedConvIds = new HashSet<>();
    for (ConvInfo conv : convList.convs()) {
      changedConvIds.add(conv.getConvId());
    }

    if (request.getConvVersionsCount() > 0) {
      for (SyncReq.ConvVersion version : request.getConvVersionsList()) {
        if (changedConvIds.contains(version.getConvId())) {
          continue;
        }
        ConvInfo conv = memberClient.getMemberConv(userId, version.getConvId());
        long serverMaxSeq = conv.getMaxSeq();
        long beginSeq = version.getLocalMaxSeq() + 1;
        addDelta(response, conv, beginSeq, serverMaxSeq, false);
      }
    }

    for (ConvInfo conv : convList.convs()) {
      if (conv.getDeleted()) {
        addDeletedDelta(response, conv);
        continue;
      }
      long beginSeq = localMaxSeqByConv.getOrDefault(conv.getConvId(), 0L) + 1;
      addDelta(response, conv, beginSeq, conv.getMaxSeq(), true);
    }

    if (request.getConvVersionsCount() == 0 && convList.convs().isEmpty()) {
      return response.build();
    }
    return response.build();
  }

  public MessagePage history(long userId, long conversationId, Long endSeq, int limit) {
    TenantContext.requiredTenantId();
    ConvInfo conv = memberClient.getMemberConv(userId, conversationId);
    long effectiveEndSeq = endSeq == null || endSeq <= 0 ? Long.MAX_VALUE : endSeq;
    int effectiveLimit = normalizeLimit(limit);
    List<MessageEntity> entities = messageMapper.selectList(Wrappers.lambdaQuery(MessageEntity.class)
        .eq(MessageEntity::getConversationId, conversationId)
        .le(MessageEntity::getSeq, effectiveEndSeq)
        .orderByDesc(MessageEntity::getSeq)
        .last("LIMIT " + (effectiveLimit + 1)));
    boolean hasMore = entities.size() > effectiveLimit;
    if (hasMore) {
      entities = entities.subList(0, effectiveLimit);
    }
    List<MsgPush> messages = entities.stream()
        .map(entity -> toPush(entity, conv.getType()))
        .toList();
    return new MessagePage(messages, hasMore, conv.getReadSeq());
  }

  public List<MsgPush> pullForRpc(PullMsgsReq request) {
    TenantContext.requiredTenantId();
    int limit = normalizeLimit(request.getLimit());
    long endSeq = request.getEndSeq() <= 0 ? Long.MAX_VALUE : request.getEndSeq();
    return range(request.getConvId(), request.getBeginSeq(), endSeq, limit,
        selectConvType(request.getConvId())).messages();
  }

  private MessagePage range(long conversationId, long beginSeq, long endSeq, int limit,
      ConvType convType) {
    if (conversationId <= 0 || beginSeq <= 0 || endSeq < beginSeq) {
      return new MessagePage(List.of(), false, 0L);
    }
    int effectiveLimit = normalizeLimit(limit);
    List<MessageEntity> entities = messageMapper.selectList(Wrappers.lambdaQuery(MessageEntity.class)
        .eq(MessageEntity::getConversationId, conversationId)
        .ge(MessageEntity::getSeq, beginSeq)
        .le(MessageEntity::getSeq, endSeq)
        .orderByAsc(MessageEntity::getSeq)
        .last("LIMIT " + (effectiveLimit + 1)));
    boolean hasMore = entities.size() > effectiveLimit;
    if (hasMore) {
      entities = entities.subList(0, effectiveLimit);
    }
    return new MessagePage(entities.stream()
        .map(entity -> toPush(entity, convType))
        .toList(), hasMore, 0L);
  }

  private void addDelta(SyncResp.Builder response, ConvInfo conv, long beginSeq, long serverMaxSeq,
      boolean includeWhenNoMessages) {
    if (serverMaxSeq <= 0) {
      if (includeWhenNoMessages) {
        response.addDeltas(SyncResp.ConvDelta.newBuilder()
            .setConv(conv.toBuilder().setMaxSeq(0L))
            .setServerMaxSeq(0L)
            .setHasMore(false));
      }
      return;
    }
    if (serverMaxSeq < beginSeq) {
      if (includeWhenNoMessages) {
        response.addDeltas(SyncResp.ConvDelta.newBuilder()
            .setConv(conv.toBuilder().setMaxSeq(serverMaxSeq))
            .setServerMaxSeq(serverMaxSeq)
            .setHasMore(false));
      }
      return;
    }
    MessagePage page = range(conv.getConvId(), beginSeq, serverMaxSeq, DEFAULT_LIMIT, conv.getType());
    response.addDeltas(SyncResp.ConvDelta.newBuilder()
        .setConv(conv.toBuilder().setMaxSeq(serverMaxSeq))
        .addAllMsgs(page.messages())
        .setServerMaxSeq(serverMaxSeq)
        .setHasMore(page.hasMore()));
  }

  private void addDeletedDelta(SyncResp.Builder response, ConvInfo conv) {
    response.addDeltas(SyncResp.ConvDelta.newBuilder()
        .setConv(conv)
        .setServerMaxSeq(conv.getMaxSeq())
        .setHasMore(false));
  }

  private int normalizeLimit(int limit) {
    if (limit <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }

  private ConvType selectConvType(long conversationId) {
    Integer type = conversationProgressMapper.selectType(conversationId);
    ConvType convType = type == null ? ConvType.CONV_TYPE_UNSPECIFIED : ConvType.forNumber(type);
    return convType == null ? ConvType.CONV_TYPE_UNSPECIFIED : convType;
  }

  private MsgPush toPush(MessageEntity entity, ConvType convType) {
    if (convType == ConvType.GROUP) {
      return assembler.toPush(entity, ConvType.GROUP);
    }
    return assembler.toPush(entity);
  }
}
