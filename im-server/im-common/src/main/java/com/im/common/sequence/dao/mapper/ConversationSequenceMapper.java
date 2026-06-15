package com.im.common.sequence.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConversationSequenceMapper {

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
}
