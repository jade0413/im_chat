package com.im.conversation.dao.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConversationUserConvVersionMapper {

  @Insert("""
      INSERT IGNORE INTO user_conv_version (tenant_id, user_id, conv_list_version)
      VALUES (#{tenantId}, #{userId}, 0)
      """)
  int insertInitial(@Param("tenantId") long tenantId, @Param("userId") long userId);

  @Update("""
      UPDATE user_conv_version
      SET conv_list_version = conv_list_version + 1
      WHERE tenant_id = #{tenantId}
        AND user_id = #{userId}
      """)
  int increment(@Param("tenantId") long tenantId, @Param("userId") long userId);

  @Select("""
      SELECT conv_list_version
      FROM user_conv_version
      WHERE tenant_id = #{tenantId}
        AND user_id = #{userId}
      """)
  Long selectVersion(@Param("tenantId") long tenantId, @Param("userId") long userId);
}
