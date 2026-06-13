package com.im.cs.widget;

/**
 * CS 模块共享常量。
 */
public final class CsConstants {

  private CsConstants() {}

  // --- user.user_type ---
  /** 普通 IM 用户（默认） */
  public static final int USER_TYPE_MEMBER  = 1;
  /** 访客（无账号，由 widget 接入） */
  public static final int USER_TYPE_VISITOR = 3;
  // user_type=2(AGENT) 在 proto 中保留但业务层改用 is_agent 标志，不在此常量中使用

  // --- user.is_agent ---
  public static final int IS_AGENT_FALSE = 0;
  public static final int IS_AGENT_TRUE  = 1;

  // --- user.agent_status ---
  public static final int AGENT_STATUS_OFFLINE = 0;
  public static final int AGENT_STATUS_ONLINE  = 1;
  public static final int AGENT_STATUS_BUSY    = 2;

  // --- conversation.cs_status（与 schema 注释对齐，1-based）---
  public static final int CS_STATUS_OPEN     = 1;
  public static final int CS_STATUS_ASSIGNED = 2;
  public static final int CS_STATUS_RESOLVED = 3;

  // --- conversation.type ---
  public static final int CONV_TYPE_CS = 3;

  // --- widget_config 默认值 ---
  public static final String WIDGET_DEFAULT_COLOR       = "#1890FF";
  public static final String WIDGET_DEFAULT_WELCOME_MSG = "有什么可以帮您？";
  public static final String WIDGET_DEFAULT_OFFLINE_MSG = "我们现在不在线，留言我们会尽快回复";
  public static final String WIDGET_DEFAULT_DISPLAY_NAME = "在线客服";
  public static final String WIDGET_DEFAULT_POSITION    = "bottom-right";
}
