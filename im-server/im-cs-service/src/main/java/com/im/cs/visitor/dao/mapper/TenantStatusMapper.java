package com.im.cs.visitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 只读查询 tenant 表状态，供免鉴权 widget 端点校验租户合法性。
 *
 * <p>{@code tenant} 表在 {@code TenantLineHandlerConfig} 的忽略列表中，
 * 不会被租户拦截器注入 tenant_id，因此可按主键直接查询。
 */
@Mapper
public interface TenantStatusMapper {

  /** 返回租户状态（1 正常 / 2 停用），租户不存在返回 {@code null}。 */
  @Select("SELECT status FROM tenant WHERE id = #{tenantId}")
  Integer selectStatus(@Param("tenantId") long tenantId);
}
