package com.im.common.mybatis;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.im.common.tenant.TenantContext;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.schema.Column;
import org.springframework.stereotype.Component;

@Component
public class TenantLineHandlerConfig implements TenantLineHandler {

  public static final String TENANT_ID_COLUMN = "tenant_id";

  private static final Set<String> IGNORED_TABLES = Set.of(
      "tenant",
      "outbox",
      "flyway_schema_history"
  );

  @Override
  public Expression getTenantId() {
    return new LongValue(TenantContext.requiredTenantId());
  }

  @Override
  public String getTenantIdColumn() {
    return TENANT_ID_COLUMN;
  }

  @Override
  public boolean ignoreTable(String tableName) {
    return tableName != null && IGNORED_TABLES.contains(normalize(tableName));
  }

  @Override
  public boolean ignoreInsert(List<Column> columns, String tenantIdColumn) {
    return columns.stream()
        .map(Column::getColumnName)
        .map(this::normalize)
        .anyMatch(normalize(tenantIdColumn)::equals);
  }

  private String normalize(String value) {
    return value.replace("`", "")
        .replace("\"", "")
        .toLowerCase(Locale.ROOT);
  }
}
