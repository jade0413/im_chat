package com.im.message.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.message.dao.entity.MessageFileMetaEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MessageFileMetaMapper extends BaseMapper<MessageFileMetaEntity> {

  @Select("""
      SELECT *
      FROM file_meta
      WHERE tenant_id = #{tenantId}
        AND object_key = #{objectKey}
      """)
  MessageFileMetaEntity selectByObjectKey(@Param("tenantId") long tenantId,
      @Param("objectKey") String objectKey);
}
