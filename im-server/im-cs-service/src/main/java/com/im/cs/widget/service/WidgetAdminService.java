package com.im.cs.widget.service;

import com.im.common.id.SnowflakeIdGenerator;
import com.im.common.tenant.TenantContext;
import com.im.cs.widget.dao.entity.WidgetConfigEntity;
import com.im.cs.widget.dao.mapper.WidgetConfigMapper;
import com.im.cs.widget.dto.UpdateWidgetConfigRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Widget 配置管理端（T35b，补 T36 缺口）。upsert：租户首次配置自动 insert，否则更新。
 * 鉴权在控制器层（需坐席权限）；公开 GET 仍走 {@link WidgetService}。
 */
@Service
public class WidgetAdminService {

  private final WidgetConfigMapper widgetConfigMapper;
  private final SnowflakeIdGenerator idGenerator;

  public WidgetAdminService(WidgetConfigMapper widgetConfigMapper, SnowflakeIdGenerator idGenerator) {
    this.widgetConfigMapper = widgetConfigMapper;
    this.idGenerator = idGenerator;
  }

  @Transactional
  public void updateConfig(UpdateWidgetConfigRequest request) {
    long tenantId = TenantContext.requiredTenantId();
    WidgetConfigEntity existing = widgetConfigMapper.findByTenant(tenantId);

    WidgetConfigEntity entity = new WidgetConfigEntity();
    entity.setColor(request.color());
    entity.setWelcomeMsg(request.welcomeMsg());
    entity.setOfflineMsg(request.offlineMsg());
    entity.setDisplayName(request.displayName());
    entity.setPosition(request.position());
    entity.setPoweredBy(Boolean.TRUE.equals(request.poweredBy()) ? 1 : 0);

    if (existing == null) {
      entity.setId(idGenerator.nextId());
      entity.setTenantId(tenantId);
      widgetConfigMapper.insert(entity);
    } else {
      entity.setId(existing.getId());
      widgetConfigMapper.updateById(entity);
    }
  }
}
