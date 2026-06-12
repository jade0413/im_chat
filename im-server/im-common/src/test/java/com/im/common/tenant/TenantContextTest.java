package com.im.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TenantContextTest {

  @Test
  void returnsEmptyWhenTenantIsNotBound() {
    assertThat(TenantContext.currentTenantId()).isEmpty();
  }

  @Test
  void requiredTenantFailsClosedWhenTenantIsMissing() {
    assertThatThrownBy(TenantContext::requiredTenantId)
        .isInstanceOf(TenantRequiredException.class)
        .hasMessage("tenant context is required");
  }

  @Test
  void bindsTenantWithinRunnableScope() {
    TenantContext.runWithTenant(1L, () ->
        assertThat(TenantContext.requiredTenantId()).isEqualTo(1L));

    assertThat(TenantContext.currentTenantId()).isEmpty();
  }

  @Test
  void bindsTenantWithinCallableScope() throws Exception {
    long tenantId = TenantContext.callWithTenant(2L, TenantContext::requiredTenantId);

    assertThat(tenantId).isEqualTo(2L);
    assertThat(TenantContext.currentTenantId()).isEmpty();
  }

  @Test
  void rejectsInvalidTenantId() {
    assertThatThrownBy(() -> TenantContext.runWithTenant(0L, () -> { }))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("tenantId must be positive");
  }
}
