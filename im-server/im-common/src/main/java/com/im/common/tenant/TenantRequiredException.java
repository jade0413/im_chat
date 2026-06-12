package com.im.common.tenant;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import java.io.Serial;

public class TenantRequiredException extends ImException {

  @Serial
  private static final long serialVersionUID = 1L;

  public TenantRequiredException() {
    super(ErrorCode.INTERNAL_ERROR, "tenant context is required");
  }
}
