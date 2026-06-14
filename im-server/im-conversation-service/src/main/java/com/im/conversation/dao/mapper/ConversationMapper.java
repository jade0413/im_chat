package com.im.conversation.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.conversation.dao.entity.ConversationEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConversationMapper extends BaseMapper<ConversationEntity> {

  /**
   * 查找访客的最新 open/assigned CS 会话（T31）。
   * cs_status: 1=open, 2=assigned, 3=resolved
   */
  @Select("""
      SELECT c.* FROM conversation c
      JOIN conversation_member cm
        ON cm.conv_id = c.id AND cm.tenant_id = c.tenant_id
      WHERE c.tenant_id = #{tenantId}
        AND c.type = 3
        AND c.cs_status IN (1, 2)
        AND cm.user_id = #{visitorUserId}
        AND cm.deleted_at IS NULL
      ORDER BY c.created_at DESC
      LIMIT 1
      """)
  ConversationEntity findOpenCsConvByVisitor(
      @Param("tenantId") long tenantId,
      @Param("visitorUserId") long visitorUserId);

  /**
   * 坐席认领会话：cs_status open(1) → assigned(2)，同时绑定 agent_id。
   * 使用乐观条件 cs_status=1 防止并发认领。
   * 返回受影响行数，0 表示会话已不是 open 状态（被抢先认领或已 resolved）。
   */
  @Update("""
      UPDATE conversation
      SET cs_status = 2, agent_id = #{agentId}
      WHERE tenant_id = #{tenantId}
        AND id = #{convId}
        AND cs_status = 1
      """)
  int claimConversation(
      @Param("tenantId") long tenantId,
      @Param("convId") long convId,
      @Param("agentId") long agentId);

  /**
   * 坐席结单：cs_status assigned(2) → resolved(3)。
   * 保留 agent_id 作为「处理坐席」记录，供质检/交接及结单后内部备注权限判定使用。
   * 要求会话当前 agent_id == #{agentId}，防止越权结单。
   */
  @Update("""
      UPDATE conversation
      SET cs_status = 3
      WHERE tenant_id = #{tenantId}
        AND id = #{convId}
        AND cs_status = 2
        AND agent_id = #{agentId}
      """)
  int resolveConversation(
      @Param("tenantId") long tenantId,
      @Param("convId") long convId,
      @Param("agentId") long agentId);

  /**
   * 坐席工作台会话列表（D33）：
   * - open(1) 的所有会话（未认领，任何坐席均可见）
   * - assigned(2) 且 agent_id = #{agentId} 的会话（仅本人）
   * 按 last_msg_time 倒序（无消息的按 created_at 倒序）。
   */
  @Select("""
      SELECT * FROM conversation
      WHERE tenant_id = #{tenantId}
        AND type = 3
        AND (
          cs_status = 1
          OR (cs_status = 2 AND agent_id = #{agentId})
        )
      ORDER BY COALESCE(last_msg_time, created_at) DESC
      LIMIT #{limit} OFFSET #{offset}
      """)
  List<ConversationEntity> listAgentCsConvs(
      @Param("tenantId") long tenantId,
      @Param("agentId") long agentId,
      @Param("limit") int limit,
      @Param("offset") int offset);
}
