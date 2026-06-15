package com.im.group.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.group.dao.entity.GroupInfoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GroupInfoMapper extends BaseMapper<GroupInfoEntity> {

  /**
   * 行锁加载群（{@code SELECT ... FOR UPDATE}）。tenant_id 由 MyBatis-Plus 租户拦截器自动注入。
   *
   * <p>用于加人/退人/改名等成员变更事务，把同一群的并发变更串行化，
   * 消除 {@code member_count} 读改写丢更新与人数上限并发越界（参见架构审查 P1-群计数）。
   */
  @Select("SELECT * FROM group_info WHERE id = #{groupId} FOR UPDATE")
  GroupInfoEntity selectByIdForUpdate(@Param("groupId") long groupId);
}
