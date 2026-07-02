import 'dart:io' show Platform;

/// 协议 Platform 数值，必须与 im-proto/common/enums.proto 的 Platform 严格一致。
/// 网关用裸 int 透传（不 import 业务包），AUTH 帧的 platform 字段就是这些值。
enum AppPlatform {
  ios(1, PlatformClass.mobile),
  android(2, PlatformClass.mobile),
  windows(3, PlatformClass.desktop),
  macos(4, PlatformClass.desktop),
  web(5, PlatformClass.web), // im-app 不用，仅占位对齐
  miniProgram(6, PlatformClass.web);

  const AppPlatform(this.protoValue, this.platformClass);

  /// 写入 AuthReq.platform 的数值
  final int protoValue;

  /// 平台类（D11：互踢矩阵按平台类计算——同类互踢、跨类共存）
  final PlatformClass platformClass;
}

/// 平台类（D11）：Mobile / Desktop / Web 三类各限 1 台在线。
enum PlatformClass { mobile, desktop, web }

/// 运行时平台探测。im-app 仅覆盖 Android / iOS / macOS / Windows 四端。
class PlatformInfo {
  PlatformInfo._();

  static final AppPlatform current = _detect();

  static AppPlatform _detect() {
    if (Platform.isIOS) return AppPlatform.ios;
    if (Platform.isAndroid) return AppPlatform.android;
    if (Platform.isWindows) return AppPlatform.windows;
    if (Platform.isMacOS) return AppPlatform.macos;
    // Linux 未在四端目标内；按 desktop 兜底，便于本地开发联调
    return AppPlatform.windows;
  }

  static bool get isMobile => current.platformClass == PlatformClass.mobile;
  static bool get isDesktop => current.platformClass == PlatformClass.desktop;

  /// Shorebird code push 仅支持移动端；桌面端走配置 OTA（见 update_service.dart）。
  static bool get supportsShorebird => isMobile;

  static String get label => switch (current) {
        AppPlatform.ios => 'iOS',
        AppPlatform.android => 'Android',
        AppPlatform.windows => 'Windows',
        AppPlatform.macos => 'macOS',
        AppPlatform.web => 'Web',
        AppPlatform.miniProgram => 'MiniProgram',
      };
}
