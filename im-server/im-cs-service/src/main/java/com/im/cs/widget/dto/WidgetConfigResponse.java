package com.im.cs.widget.dto;

/**
 * Widget 配置响应（D37，T36）。
 *
 * @param color        品牌主色（hex，如 "#1890FF"）
 * @param welcomeMsg   在线欢迎语
 * @param offlineMsg   无坐席在线时的离线提示
 * @param displayName  Widget 头部展示名称（如 "在线客服"）
 * @param position     悬浮位置：bottom-right | bottom-left
 * @param poweredBy    是否显示 "Powered by" 徽标（D37 免费版增长机制）
 */
public record WidgetConfigResponse(
    String color,
    String welcomeMsg,
    String offlineMsg,
    String displayName,
    String position,
    boolean poweredBy
) {}
