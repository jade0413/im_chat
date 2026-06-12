package com.im.push.route;

import com.im.common.device.PlatformClass;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.proto.rpc.ConnCtx;

public record OnlineRoute(
    long tenantId,
    long userId,
    int platform,
    String platformClass,
    String deviceId,
    String connId,
    String gwInstance
) {

  public static OnlineRoute from(ConnCtx ctx) {
    if (ctx == null || ctx.getTenantId() <= 0 || ctx.getUserId() <= 0
        || ctx.getConnId().isBlank() || ctx.getGwInstance().isBlank()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "valid connection context is required");
    }
    PlatformClass platformClass = PlatformClass.fromPlatform(ctx.getPlatform());
    return new OnlineRoute(
        ctx.getTenantId(),
        ctx.getUserId(),
        ctx.getPlatform(),
        platformClass.key(),
        ctx.getDeviceId(),
        ctx.getConnId(),
        ctx.getGwInstance());
  }

  public boolean sameConnection(OnlineRoute other) {
    return other != null
        && tenantId == other.tenantId
        && userId == other.userId
        && platformClass.equals(other.platformClass)
        && connId.equals(other.connId)
        && gwInstance.equals(other.gwInstance);
  }
}
