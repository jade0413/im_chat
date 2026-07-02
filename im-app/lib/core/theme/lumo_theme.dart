import 'package:flutter/material.dart';
import 'lumo_colors.dart';

/// 微光 Lumo 主题。布局遵循各平台用户惯性——桌面三栏、移动单栏 + 底部 Tab，
/// 视觉共享同一套品牌色、圆角、气泡与间距。
class LumoTheme {
  LumoTheme._();

  /// 拉丁字体 Plus Jakarta Sans；中文回退到系统苹方/微软雅黑（fontFamilyFallback）。
  static const String _fontFamily = 'PlusJakartaSans';
  static const List<String> _fallback = [
    'PingFang SC', // macOS / iOS
    'Microsoft YaHei', // Windows
    'Noto Sans CJK SC', // Android / Linux
    'sans-serif',
  ];

  static const double radius = 14; // 统一圆角
  static const double bubbleRadius = 18;

  static ThemeData light() => _build(Brightness.light);
  static ThemeData dark() => _build(Brightness.dark);

  static ThemeData _build(Brightness brightness) {
    final isDark = brightness == Brightness.dark;
    final scheme = ColorScheme.fromSeed(
      seedColor: LumoColors.primary,
      brightness: brightness,
      primary: LumoColors.primary,
      onPrimary: LumoColors.textOnPrimary,
      primaryContainer: LumoColors.primarySoft,
      error: LumoColors.danger,
      surface: isDark ? LumoColors.darkSurface : LumoColors.surface,
    );

    final ink = isDark ? LumoColors.darkInk : LumoColors.ink;
    final secondary =
        isDark ? LumoColors.darkTextSecondary : LumoColors.textSecondary;

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: scheme,
      scaffoldBackgroundColor: isDark ? LumoColors.darkBg : LumoColors.bg,
      fontFamily: _fontFamily,
      fontFamilyFallback: _fallback,
      dividerTheme: DividerThemeData(
        color: isDark ? LumoColors.darkDivider : LumoColors.divider,
        thickness: 0.6,
        space: 0.6,
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: isDark ? LumoColors.darkSurface : LumoColors.surface,
        foregroundColor: ink,
        elevation: 0,
        scrolledUnderElevation: 0.5,
        centerTitle: false,
        titleTextStyle: TextStyle(
          fontFamily: _fontFamily,
          fontFamilyFallback: _fallback,
          fontWeight: FontWeight.w600,
          fontSize: 17,
          color: ink,
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: LumoColors.primary,
          foregroundColor: LumoColors.textOnPrimary,
          minimumSize: const Size.fromHeight(48),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(radius),
          ),
          textStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: isDark ? LumoColors.darkSurfaceAlt : LumoColors.surfaceAlt,
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radius),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radius),
          borderSide: BorderSide.none,
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radius),
          borderSide: const BorderSide(color: LumoColors.primary, width: 1.5),
        ),
        hintStyle: TextStyle(color: secondary),
      ),
      textTheme: _textTheme(ink, secondary),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: isDark ? LumoColors.darkSurface : LumoColors.surface,
        indicatorColor: LumoColors.primarySoft,
        labelTextStyle: WidgetStateProperty.resolveWith(
          (states) => TextStyle(
            fontSize: 11,
            fontWeight: FontWeight.w500,
            color: states.contains(WidgetState.selected)
                ? LumoColors.primary
                : secondary,
          ),
        ),
      ),
      extensions: [LumoBubbleTheme.of(isDark)],
    );
  }

  static TextTheme _textTheme(Color ink, Color secondary) => TextTheme(
        titleLarge: TextStyle(color: ink, fontWeight: FontWeight.w700),
        titleMedium: TextStyle(color: ink, fontWeight: FontWeight.w600),
        bodyLarge: TextStyle(color: ink, fontSize: 15, height: 1.35),
        bodyMedium: TextStyle(color: ink, fontSize: 14, height: 1.35),
        bodySmall: TextStyle(color: secondary, fontSize: 12),
        labelSmall: TextStyle(color: secondary, fontSize: 11),
      );
}

/// 气泡配色作为 ThemeExtension，气泡组件按当前明暗取值，避免硬编码。
class LumoBubbleTheme extends ThemeExtension<LumoBubbleTheme> {
  const LumoBubbleTheme({
    required this.selfBg,
    required this.selfText,
    required this.otherBg,
    required this.otherText,
  });

  final Color selfBg;
  final Color selfText;
  final Color otherBg;
  final Color otherText;

  factory LumoBubbleTheme.of(bool isDark) => LumoBubbleTheme(
        selfBg: isDark ? LumoColors.bubbleSelfDark : LumoColors.bubbleSelf,
        selfText: isDark ? LumoColors.darkInk : LumoColors.bubbleSelfText,
        otherBg: isDark ? LumoColors.darkBubbleOther : LumoColors.bubbleOther,
        otherText: isDark ? LumoColors.darkInk : LumoColors.bubbleOtherText,
      );

  @override
  LumoBubbleTheme copyWith({
    Color? selfBg,
    Color? selfText,
    Color? otherBg,
    Color? otherText,
  }) =>
      LumoBubbleTheme(
        selfBg: selfBg ?? this.selfBg,
        selfText: selfText ?? this.selfText,
        otherBg: otherBg ?? this.otherBg,
        otherText: otherText ?? this.otherText,
      );

  @override
  LumoBubbleTheme lerp(LumoBubbleTheme? other, double t) {
    if (other == null) return this;
    return LumoBubbleTheme(
      selfBg: Color.lerp(selfBg, other.selfBg, t)!,
      selfText: Color.lerp(selfText, other.selfText, t)!,
      otherBg: Color.lerp(otherBg, other.otherBg, t)!,
      otherText: Color.lerp(otherText, other.otherText, t)!,
    );
  }
}
