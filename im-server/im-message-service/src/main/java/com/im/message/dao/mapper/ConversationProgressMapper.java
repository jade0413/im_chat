package com.im.message.dao.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConversationProgressMapper {

  @Update("""
      UPDATE conversation
      SET max_seq = max_seq + 1
      WHERE id = #{conversationId}
      """)
  int incrementMaxSeq(@Param("conversationId") long conversationId);

  @Select("""
      SELECT max_seq
      FROM conversation
      WHERE id = #{conversationId}
      """)
  Long selectMaxSeq(@Param("conversationId") long conversationId);

  @Select("""
      SELECT type
      FROM conversation
      WHERE id = #{conversationId}
      """)
  Integer selectType(@Param("conversationId") long conversationId);

  @Update("""
      UPDATE conversation
      SET last_msg_abstract = #{lastMsgAbstract},
          last_msg_time = #{lastMsgTime}
      WHERE id = #{conversationId}
        AND max_seq = #{seq}
      """)
  int updateLastMessage(@Param("conversationId") long conversationId,
      @Param("seq") long seq,
      @Param("lastMsgAbstract") String lastMsgAbstract,
      @Param("lastMsgTime") LocalDateTime lastMsgTime);
}
