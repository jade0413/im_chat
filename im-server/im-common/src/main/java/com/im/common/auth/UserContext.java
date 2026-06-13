package com.im.common.auth;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;

/**
 * 每个虚拟线程内当前登录用户的身份。
 * 由 JwtAuthInterceptor 在请求入口填充，由虚拟线程隔离保证无泄漏。
 */
public final class UserContext {

  private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

  private UserContext() {
  }

  public static void set(long userId) {
    HOLDER.set(userId);
  }

  public static Long get() {
    return HOLDER.get();
  }

  /** 获取当前用户 ID，未设置时抛出 TOKEN_INVALID 异常。 */
  public static long requiredUserId() {
    Long userId = HOLDER.get();
    if (userId == null) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    return userId;
  }

  public static void clear() {
    HOLDER.remove();
  }
}
