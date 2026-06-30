package com.im.common.sequence.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConversationSequenceMapper {

  /**
   * 自增并把新值写入连接本地 LAST_INSERT_ID()，避免随后再回表 SELECT 一次热点会话行（C-2）。
   * 仅当本语句确实更新了 1 行（updated==1）时，LAST_INSERT_ID() 才反映本次自增值。
   */
  @Update("""
      UPDATE conversation
      SET max_seq = LAST_INSERT_ID(max_seq + 1)
      WHERE id = #{conversationId}
      """)
  int incrementMaxSeq(@Param("conversationId") long conversationId);

  /** 取本连接最近一次 LAST_INSERT_ID(expr) 设置的值（连接本地，不访问会话行）。 */
  @Select("SELECT LAST_INSERT_ID()")
  long selectAllocatedSeq();

  @Select("""
      SELECT max_seq
      FROM conversation
      WHERE id = #{conversationId}
      """)
  Long selectMaxSeq(@Param("conversationId") long conversationId);
}
