package com.im.message.integration;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface ConversationSeedMapper {

  @Insert("""
      INSERT INTO conversation (id, type, c2c_key, max_seq, last_msg_abstract)
      VALUES (#{conversationId}, 1, #{c2cKey}, 0, '')
      """)
  int insertConversation(@Param("conversationId") long conversationId, @Param("c2cKey") String c2cKey);

  @Select("SELECT max_seq FROM conversation WHERE id = #{conversationId}")
  Long selectMaxSeq(@Param("conversationId") long conversationId);
}
