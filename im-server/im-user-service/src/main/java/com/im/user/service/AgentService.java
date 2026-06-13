package com.im.user.service;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.user.dao.entity.UserEntity;
import com.im.user.dao.mapper.UserMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 坐席身份与在线状态管理（D34 / D35 / T35）。
 *
 * <p>is_agent 和 agent_status 列由 V6 migration 新增（见 V6__cs_tables.sql）。
 */
@Service
public class AgentService {

  /** 坐席在线状态合法值范围（D35）。 */
  private static final int AGENT_STATUS_MIN = 0; // offline
  private static final int AGENT_STATUS_MAX = 2; // busy

  private final UserMapper userMapper;

  public AgentService(UserMapper userMapper) {
    this.userMapper = userMapper;
  }

  /**
   * 检查指定用户是否拥有坐席权限。
   * 查询当前租户下该用户的 is_agent 列，0=普通用户，1=坐席（D34）。
   */
  public boolean isAgent(long userId) {
    UserEntity user = userMapper.selectById(userId);
    return user != null
        && Integer.valueOf(1).equals(user.getIsAgent());
  }

  /**
   * 检查租户内是否有坐席在线（online 或 busy）（D35）。
   * 用于 Widget availability API，结果精确到秒级（DB 列非实时 Redis）。
   *
   * @return 在线坐席数量（0 表示无坐席在线）
   */
  public int countOnlineAgents(long tenantId) {
    return userMapper.countOnlineAgents(tenantId);
  }

  /**
   * 获取租户所有 online/busy 坐席的 user_id（D33: CS open 会话推送扇出）。
   * 使用 DB 列（非 Redis 路由表），准确性依赖坐席主动调用 UpdateAgentStatus。
   */
  public List<Long> getOnlineAgentIds(long tenantId) {
    return userMapper.findOnlineAgentIds(tenantId);
  }

  /**
   * 更新坐席在线状态（D35）。
   *
   * @param userId      坐席 user_id
   * @param agentStatus 新状态：0=offline, 1=online, 2=busy
   * @throws ImException VALIDATION_FAILED 参数非法；NO_PERMISSION 非坐席用户
   */
  public void updateStatus(long userId, int agentStatus) {
    if (agentStatus < AGENT_STATUS_MIN || agentStatus > AGENT_STATUS_MAX) {
      throw new ImException(ErrorCode.VALIDATION_FAILED,
          "agent_status 必须为 0(offline)/1(online)/2(busy)，收到: " + agentStatus);
    }
    UserEntity user = userMapper.selectById(userId);
    if (user == null || !Integer.valueOf(1).equals(user.getIsAgent())) {
      throw new ImException(ErrorCode.NO_PERMISSION, "当前用户不是坐席，userId=" + userId);
    }
    UserEntity update = new UserEntity();
    update.setId(userId);
    update.setAgentStatus(agentStatus);
    userMapper.updateById(update);
  }
}
