package com.im.common.tenant;

import java.util.Optional;
import java.util.concurrent.Callable;

public final class TenantContext {

  private static final ScopedValue<Long> TENANT_ID = ScopedValue.newInstance();

  private TenantContext() {
  }

  public static Optional<Long> currentTenantId() {
    if (!TENANT_ID.isBound()) {
      return Optional.empty();
    }
    return Optional.of(TENANT_ID.get());
  }

  public static long requiredTenantId() {
    return currentTenantId().orElseThrow(TenantRequiredException::new);
  }

  public static void runWithTenant(long tenantId, Runnable action) {
    validateTenantId(tenantId);
    ScopedValue.runWhere(TENANT_ID, tenantId, action);
  }

  public static <T> T callWithTenant(long tenantId, Callable<T> action) throws Exception {
    validateTenantId(tenantId);
    return ScopedValue.callWhere(TENANT_ID, tenantId, action);
  }

  private static void validateTenantId(long tenantId) {
    if (tenantId <= 0) {
      throw new IllegalArgumentException("tenantId must be positive");
    }
  }
}
