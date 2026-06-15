package com.im.user.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.im.user.dao.entity.UserEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

  /**
   * 统计租户内 online/busy 坐席数（D35）。
   * 用于 widget 可用性检查，结果可能有轻微延迟（MySQL 列，非 Redis 实时）。
   */
  @Select("""
      SELECT COUNT(*) FROM `user`
      WHERE tenant_id = #{tenantId}
        AND is_agent = 1
        AND agent_status IN (1, 2)
        AND status = 1
      """)
  int countOnlineAgents(@Param("tenantId") long tenantId);

  /**
   * 获取租户所有 online/busy 坐席的 user_id（D33: CS open 会话推送扇出用）。
   * 注意：status=1 表示账号未封禁（与 D17 用户状态一致）。
   */
  @Select("""
      SELECT id FROM `user`
      WHERE tenant_id = #{tenantId}
        AND is_agent = 1
        AND agent_status IN (1, 2)
        AND status = 1
      """)
  List<Long> findOnlineAgentIds(@Param("tenantId") long tenantId);

  /**
   * 加好友查找（D42），排除自身、仅 member。**精确匹配** username 或 account（完整手机号），
   * 不做任何前缀/昵称模糊——昵称不唯一且前缀可枚举即隐私泄露。结果至多 1 条，LIMIT 兜底。
   */
  @Select("""
      SELECT id, nickname, avatar, user_type, verified_type, username FROM `user`
      WHERE tenant_id = #{tenantId}
        AND status = 1
        AND id != #{excludeUserId}
        AND user_type = 1
        AND (username = #{keyword} OR account = #{keyword})
      LIMIT 5
      """)
  List<UserEntity> searchUsers(
      @Param("tenantId") long tenantId,
      @Param("keyword") String keyword,
      @Param("excludeUserId") long excludeUserId);

  /**
   * 批量查询用户公开资料（供历史消息填充昵称/头像）。
   * 调用方控制列表大小（建议 ≤50）。
   */
  @Select("""
      <script>
      SELECT id, nickname, avatar, user_type, verified_type, username FROM `user`
      WHERE tenant_id = #{tenantId} AND id IN
        <foreach item='id' collection='ids' open='(' separator=',' close=')'>
          #{id}
        </foreach>
      </script>
      """)
  List<UserEntity> findByIds(
      @Param("tenantId") long tenantId,
      @Param("ids") List<Long> ids);
}
