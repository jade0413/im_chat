package com.im.message.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.message.dao.entity.MessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MessageMapper extends BaseMapper<MessageEntity> {

  @Select("""
      SELECT *
      FROM message
      WHERE tenant_id = #{tenantId}
        AND conversation_id = #{conversationId}
        AND seq = #{seq}
      """)
  MessageEntity selectByConversationSeq(@Param("tenantId") long tenantId,
      @Param("conversationId") long conversationId,
      @Param("seq") long seq);

  @Update("""
      UPDATE message
      SET status = #{status},
          revoke_reason = #{revokeReason}
      WHERE tenant_id = #{tenantId}
        AND conversation_id = #{conversationId}
        AND seq = #{seq}
        AND status <> #{status}
      """)
  int markRevoked(@Param("tenantId") long tenantId,
      @Param("conversationId") long conversationId,
      @Param("seq") long seq,
      @Param("status") int status,
      @Param("revokeReason") int revokeReason);
}
