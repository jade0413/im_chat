package com.im.message.moderation.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.message.moderation.dao.entity.SensitiveWordEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWordEntity> {

  @Select("""
      SELECT *
      FROM sensitive_word
      WHERE enabled = 1
        AND (tenant_id IS NULL OR tenant_id = #{tenantId})
      ORDER BY
        CASE WHEN tenant_id IS NULL THEN 0 ELSE 1 END DESC,
        CHAR_LENGTH(word) DESC,
        id ASC
      """)
  List<SensitiveWordEntity> selectEnabledForTenant(@Param("tenantId") long tenantId);
}
