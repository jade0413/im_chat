package com.im.conversation.service;

import com.im.common.conversation.UserConvEventType;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.id.SnowflakeIdGenerator;
import com.im.conversation.dao.entity.ConversationEntity;
import com.im.conversation.dao.entity.ConversationMemberEntity;
import com.im.conversation.dao.mapper.ConversationMapper;
import com.im.conversation.dao.mapper.ConversationMemberMapper;
import com.im.proto.common.ConvType;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CS 会话的查找与创建（T31）。
 *
 * <p>职责边界：本 Service 仅管理 conversation / conversation_member 表的 CS 子集。
 * 坐席分配状态机由 im-cs-service 通过 gRPC 调用，进一步更新 agent_id 和 cs_status。
 */
@Service
public class CsConversationService {

  /** cs_status 1=open, 2=assigned（与 V1 schema 注释及 CsConstants 对齐）*/
  private static final int CS_STATUS_OPEN = 1;
  /** conversation.type = 3（CS_SESSION） */
  private static final int CONV_TYPE_CS = ConvType.CS_SESSION.getNumber();

  private final ConversationMapper conversationMapper;
  private final ConversationMemberMapper memberMapper;
  private final SnowflakeIdGenerator idGenerator;
  private final UserConvEventRecorder userConvEventRecorder;

  public CsConversationService(ConversationMapper conversationMapper,
      ConversationMemberMapper memberMapper,
      SnowflakeIdGenerator idGenerator,
      UserConvEventRecorder userConvEventRecorder) {
    this.conversationMapper = conversationMapper;
    this.memberMapper = memberMapper;
    this.idGenerator = idGenerator;
    this.userConvEventRecorder = userConvEventRecorder;
  }

  /**
   * 查找访客的 open/assigned CS 会话；若不存在则创建新会话（cs_status=open）。
   *
   * @param tenantId       租户 ID
   * @param visitorUserId  访客 user_id（user_type=VISITOR）
   * @return 结果包装
   */
  @Transactional
  public FindOrCreateResult findOrCreateCsConv(long tenantId, long visitorUserId) {
    // 优先续旧（open 或 assigned 状态的会话）
    ConversationEntity existing =
        conversationMapper.findOpenCsConvByVisitor(tenantId, visitorUserId);
    if (existing != null) {
      return new FindOrCreateResult(existing.getId(), false, existing.getCsStatus());
    }

    // 创建新 CS 会话
    ConversationEntity conv = new ConversationEntity();
    conv.setId(idGenerator.nextId());
    conv.setTenantId(tenantId);
    conv.setType(CONV_TYPE_CS);
    conv.setMaxSeq(0L);
    conv.setLastMsgAbstract("");
    conv.setCsStatus(CS_STATUS_OPEN);
    conversationMapper.insert(conv);

    // 访客加入会话（坐席通过 agent_id 跟踪，不进 conversation_member）
    ConversationMemberEntity member = new ConversationMemberEntity();
    member.setConvId(conv.getId());
    member.setTenantId(tenantId);
    member.setUserId(visitorUserId);
    member.setReadSeq(0L);
    member.setPinned(0);
    member.setMuted(0);
    memberMapper.insert(member);

    userConvEventRecorder.record(tenantId, visitorUserId, conv.getId(), UserConvEventType.CREATED);

    return new FindOrCreateResult(conv.getId(), true, CS_STATUS_OPEN);
  }

  /**
   * 坐席认领 open 会话 → assigned（D31）。
   * 使用 claimConversation 的条件更新（cs_status=1 才更新）防止并发认领。
   *
   * @throws ImException CONFLICT 已被认领；NOT_FOUND 会话不存在
   */
  public void claimConv(long tenantId, long convId, long agentId) {
    // 先验证会话存在且属于本租户
    ConversationEntity conv = conversationMapper.selectById(convId);
    if (conv == null || !conv.getTenantId().equals(tenantId) || conv.getType() != CONV_TYPE_CS) {
      throw new ImException(ErrorCode.CONV_NOT_FOUND, "CS 会话不存在: " + convId);
    }
    int updated = conversationMapper.claimConversation(tenantId, convId, agentId);
    if (updated == 0) {
      // 0 rows affected = cs_status 不是 open（已被认领或已 resolved）
      throw new ImException(ErrorCode.NO_PERMISSION, "会话已被认领或状态不是 open，convId=" + convId);
    }
  }

  /**
   * 坐席结单 assigned → resolved（D31）。
   * 要求当前 agent_id == agentId，防止越权结单。
   *
   * @throws ImException FORBIDDEN 不是当前坐席；NOT_FOUND 会话不存在
   */
  public void resolveConv(long tenantId, long convId, long agentId) {
    ConversationEntity conv = conversationMapper.selectById(convId);
    if (conv == null || !conv.getTenantId().equals(tenantId) || conv.getType() != CONV_TYPE_CS) {
      throw new ImException(ErrorCode.CONV_NOT_FOUND, "CS 会话不存在: " + convId);
    }
    int updated = conversationMapper.resolveConversation(tenantId, convId, agentId);
    if (updated == 0) {
      throw new ImException(ErrorCode.NO_PERMISSION, "无权结单：会话未分配给你或状态不是 assigned，convId=" + convId);
    }
  }

  /**
   * 坐席工作台会话列表（D33）：open 会话全部 + assigned 给本坐席的会话，按最新消息时间倒序。
   */
  public List<ConversationEntity> listAgentCsConvs(long tenantId, long agentId, int limit, int offset) {
    return conversationMapper.listAgentCsConvs(tenantId, agentId, limit, offset);
  }

  /** 取单个 CS 会话元数据（坐席侧用）。 */
  public ConversationEntity getCsConv(long tenantId, long convId) {
    ConversationEntity conv = conversationMapper.selectById(convId);
    if (conv == null || !conv.getTenantId().equals(tenantId) || conv.getType() != CONV_TYPE_CS) {
      throw new ImException(ErrorCode.CONV_NOT_FOUND, "CS 会话不存在: " + convId);
    }
    return conv;
  }

  /** 查找/创建结果。 */
  public record FindOrCreateResult(long convId, boolean isNew, int csStatus) {}
}
