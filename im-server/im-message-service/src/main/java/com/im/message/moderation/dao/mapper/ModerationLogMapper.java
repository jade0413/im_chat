package com.im.message.moderation.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.message.moderation.dao.entity.ModerationLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ModerationLogMapper extends BaseMapper<ModerationLogEntity> {

  @Select("""
      SELECT *
      FROM moderation_log
      WHERE tenant_id = #{tenantId}
        AND message_id = #{messageId}
        AND provider = #{provider}
      LIMIT 1
      """)
  ModerationLogEntity selectByMessageAndProvider(@Param("tenantId") long tenantId,
      @Param("messageId") long messageId,
      @Param("provider") String provider);

  @Select("""
      SELECT *
      FROM moderation_log
      WHERE tenant_id = #{tenantId}
        AND message_id = #{messageId}
      LIMIT 1
      """)
  ModerationLogEntity selectFirstByMessage(@Param("tenantId") long tenantId,
      @Param("messageId") long messageId);
}
