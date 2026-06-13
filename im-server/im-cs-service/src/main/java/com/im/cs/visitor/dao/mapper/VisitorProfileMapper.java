package com.im.cs.visitor.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.cs.visitor.dao.entity.VisitorProfileEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VisitorProfileMapper extends BaseMapper<VisitorProfileEntity> {

  @Select("SELECT * FROM visitor_profile WHERE tenant_id = #{tenantId} AND visitor_token = #{visitorToken} LIMIT 1")
  VisitorProfileEntity findByToken(long tenantId, String visitorToken);
}
