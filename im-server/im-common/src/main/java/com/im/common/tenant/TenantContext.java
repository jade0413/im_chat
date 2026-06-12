package com.im.common.tenant;

import java.util.Optional;
import java.util.concurrent.Callable;

public final class TenantContext {

  private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

  private TenantContext() {
  }

  public static Optional<Long> currentTenantId() {
    return Optional.ofNullable(TENANT_ID.get());
  }

  public static long requiredTenantId() {
    return currentTenantId().orElseThrow(TenantRequiredException::new);
  }

  public static void runWithTenant(long tenantId, Runnable action) {
    validateTenantId(tenantId);
    Long previous = TENANT_ID.get();
    TENANT_ID.set(tenantId);
    try {
      action.run();
    } finally {
      restore(previous);
    }
  }

  public static <T> T callWithTenant(long tenantId, Callable<T> action) throws Exception {
    validateTenantId(tenantId);
    Long previous = TENANT_ID.get();
    TENANT_ID.set(tenantId);
    try {
      return action.call();
    } finally {
      restore(previous);
    }
  }

  private static void validateTenantId(long tenantId) {
    if (tenantId <= 0) {
      throw new IllegalArgumentException("tenantId must be positive");
    }
  }

  private static void restore(Long previous) {
    if (previous == null) {
      TENANT_ID.remove();
    } else {
      TENANT_ID.set(previous);
    }
  }
}
