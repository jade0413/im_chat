package com.im.message.dao.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 消息保留清理（retention purge）所需的最小查询集合。
 *
 * <p>{@code tenant} 表在 {@code TenantLineHandlerConfig} 忽略列表中，
 * {@link #activeTenantIds()} 不会被注入 tenant_id，可在无租户上下文的后台线程直接调用。
 * 而 {@link #retentionDays(long)} / {@link #purgeOlderThan} 作用于受租户拦截器约束的表，
 * 必须在 {@code TenantContext.runWithTenant(tenantId, ...)} 内调用。
 */
@Mapper
public interface MessageRetentionMapper {

  /** 所有正常状态租户 ID（tenant 表忽略租户拦截器，可无上下文调用）。 */
  @Select("SELECT id FROM tenant WHERE status = 1")
  List<Long> activeTenantIds();

  /** 租户消息保留天数；无配置行返回 null。须在 runWithTenant 内调用。 */
  @Select("SELECT msg_retention_days FROM tenant_config WHERE tenant_id = #{tenantId}")
  Integer retentionDays(@Param("tenantId") long tenantId);

  /**
   * 删除该租户早于 cutoff 的一批消息，返回删除行数。须在 runWithTenant 内调用。
   *
   * <p>显式带 tenant_id 便于走 idx_tenant_created；租户拦截器仍会注入同值条件（幂等无害）。
   */
  @Delete("DELETE FROM message WHERE tenant_id = #{tenantId} AND created_at < #{cutoff} "
      + "LIMIT #{batchSize}")
  int purgeOlderThan(@Param("tenantId") long tenantId,
      @Param("cutoff") LocalDateTime cutoff,
      @Param("batchSize") int batchSize);
}
