package com.im.cs.widget.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.cs.widget.dao.entity.WidgetConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WidgetConfigMapper extends BaseMapper<WidgetConfigEntity> {

  @Select("SELECT * FROM widget_config WHERE tenant_id = #{tenantId} LIMIT 1")
  WidgetConfigEntity findByTenant(@Param("tenantId") long tenantId);
}
