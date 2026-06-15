package com.im.user.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 读取当前租户的好友制开关（D17）。tenant_id 由 MyBatis 租户拦截器自动注入 WHERE，
 * 无行时返回 null（按默认 0=关闭处理）。
 */
@Mapper
public interface TenantConfigMapper {

  @Select("SELECT friend_required FROM tenant_config")
  Integer selectFriendRequired();
}
