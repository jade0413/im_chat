package com.im.common.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.im.common.tenant.TenantContext;
import com.im.common.tenant.TenantRequiredException;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class TenantLineHandlerConfigTest {

  private final TenantLineHandlerConfig tenantLineHandler = new TenantLineHandlerConfig();
  private final TenantLineInnerInterceptor interceptor = new TenantLineInnerInterceptor(tenantLineHandler);

  @Test
  void injectsTenantConditionForSelect() throws Exception {
    String sql = parseWithTenant("SELECT id, sender_id FROM message WHERE status = 1");

    assertTenantPredicate(sql);
    assertThat(sql).containsIgnoringCase("WHERE");
  }

  @Test
  void injectsTenantConditionForUpdate() throws Exception {
    String sql = parseWithTenant("UPDATE message SET status = 2 WHERE id = 1");

    assertTenantPredicate(sql);
    assertThat(sql).containsIgnoringCase("UPDATE message");
  }

  @Test
  void injectsTenantConditionForDelete() throws Exception {
    String sql = parseWithTenant("DELETE FROM message WHERE id = 1");

    assertTenantPredicate(sql);
    assertThat(sql).containsIgnoringCase("DELETE FROM message");
  }

  @Test
  void injectsTenantColumnForInsert() throws Exception {
    String sql = parseWithTenant("INSERT INTO message (id, conversation_id) VALUES (1, 2)");
    String normalized = normalize(sql);

    assertThat(normalized).contains("tenant_id");
    assertThat(normalized).contains("100");
    assertThat(normalized).contains("insert into message");
  }

  @Test
  void keepsExplicitTenantColumnForInsert() throws Exception {
    String sql = parseWithTenant("INSERT INTO message (id, tenant_id, conversation_id) VALUES (1, 100, 2)");
    String normalized = normalize(sql);

    assertThat(countOccurrences(normalized, "tenant_id")).isEqualTo(1);
    assertThat(normalized).contains("100");
  }

  @Test
  void ignoresTenantTable() throws Exception {
    String sql = parseWithTenant("SELECT id, name FROM tenant WHERE id = 1");

    assertThat(normalize(sql)).doesNotContain("tenant_id");
  }

  @Test
  void failsClosedWhenTenantContextIsMissing() {
    assertThatThrownBy(() -> interceptor.parserSingle("SELECT id FROM message", null))
        .isInstanceOf(TenantRequiredException.class);
  }

  private String parseWithTenant(String sql) throws Exception {
    return TenantContext.callWithTenant(100L, () -> interceptor.parserSingle(sql, null));
  }

  private void assertTenantPredicate(String sql) {
    String normalized = normalize(sql);
    assertThat(normalized).contains("tenant_id");
    assertThat(normalized).contains("100");
  }

  private String normalize(String sql) {
    return sql.replace("`", "")
        .replace("\"", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", " ");
  }

  private int countOccurrences(String value, String needle) {
    int count = 0;
    int index = value.indexOf(needle);
    while (index >= 0) {
      count++;
      index = value.indexOf(needle, index + needle.length());
    }
    return count;
  }
}
