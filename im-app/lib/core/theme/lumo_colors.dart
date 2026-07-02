import 'package:flutter/material.dart';

/// 微光 Lumo 设计语言 · 色板（取自《微光 IM 多端设计》品牌 & 功能色）。
/// 一套色四端落地，确保 iPhone / Android / Mac / Windows 体验如一。
class LumoColors {
  LumoColors._();

  // ── 品牌 & 功能色（PDF 标注）──────────────────────────
  static const Color primary = Color(0xFF5A54F0); // 主品牌紫（自己气泡 / 主按钮）
  static const Color primarySoft = Color(0xFFECEBFE); // 浅紫（次级背景 / 选中态）
  static const Color ink = Color(0xFF16171D); // 主文字 / 深色
  static const Color success = Color(0xFF21C16B); // 在线 / 成功（绿）
  static const Color danger = Color(0xFFFF4D5E); // 未读角标 / 错误（红）

  // ── 中性色（在品牌色基础上推导，保证层次）──────────────
  static const Color bg = Color(0xFFF7F7FA); // 列表 / 页面底
  static const Color surface = Color(0xFFFFFFFF); // 卡片 / 气泡（对方）
  static const Color surfaceAlt = Color(0xFFF1F2F5); // 输入框 / 次级面
  static const Color divider = Color(0xFFE8E8EE);
  static const Color textSecondary = Color(0xFF8A8D98); // 次级文字 / 时间
  static const Color textOnPrimary = Color(0xFFFFFFFF);

  // 气泡
  static const Color bubbleSelf = Color(0xFFE6FFD8);
  static const Color bubbleSelfDark = Color(0xFF1F4E31);
  static const Color bubbleSelfText = ink;
  static const Color bubbleSelfMeta = Color(0xFF18A23A);
  static const Color bubbleOther = surface;
  static const Color bubbleOtherText = ink;

  // ── 深色模式 ────────────────────────────────────────────
  static const Color darkBg = Color(0xFF0F1014);
  static const Color darkSurface = Color(0xFF1A1B22);
  static const Color darkSurfaceAlt = Color(0xFF23242D);
  static const Color darkDivider = Color(0xFF2A2C36);
  static const Color darkInk = Color(0xFFECEDF2);
  static const Color darkTextSecondary = Color(0xFF8A8D98);
  static const Color darkBubbleOther = Color(0xFF23242D);
}
