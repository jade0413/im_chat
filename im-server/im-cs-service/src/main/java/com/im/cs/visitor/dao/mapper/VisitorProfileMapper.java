package com.im.cs.visitor.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.cs.visitor.dao.entity.VisitorProfileEntity;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VisitorProfileMapper extends BaseMapper<VisitorProfileEntity> {

  @Select("SELECT * FROM visitor_profile WHERE tenant_id = #{tenantId} AND visitor_token = #{visitorToken} LIMIT 1")
  VisitorProfileEntity findByToken(@Param("tenantId") long tenantId, @Param("visitorToken") String visitorToken);

  @Select("SELECT * FROM visitor_profile WHERE tenant_id = #{tenantId} AND user_id = #{userId} LIMIT 1")
  VisitorProfileEntity findByUserId(@Param("tenantId") long tenantId, @Param("userId") long userId);

  /** 批量查访客资料（坐席工作台列表用），调用方需保证 userIds 非空。 */
  @Select("""
      <script>
      SELECT * FROM visitor_profile
      WHERE tenant_id = #{tenantId} AND user_id IN
        <foreach item='id' collection='userIds' open='(' separator=',' close=')'>
          #{id}
        </foreach>
      </script>
      """)
  List<VisitorProfileEntity> findByUserIds(@Param("tenantId") long tenantId,
      @Param("userIds") Collection<Long> userIds);
}
