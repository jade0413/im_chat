package com.im.conversation.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.conversation.dao.entity.UserConvEventEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConversationUserConvEventMapper extends BaseMapper<UserConvEventEntity> {

  @Select("""
      SELECT *
      FROM user_conv_event
      WHERE tenant_id = #{tenantId}
        AND user_id = #{userId}
        AND event_version > #{afterVersion}
      ORDER BY event_version ASC
      LIMIT #{limit}
      """)
  List<UserConvEventEntity> selectAfterVersion(@Param("tenantId") long tenantId,
      @Param("userId") long userId,
      @Param("afterVersion") long afterVersion,
      @Param("limit") int limit);
}
