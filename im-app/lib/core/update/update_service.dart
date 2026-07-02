import 'dart:async';

import 'package:dio/dio.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shorebird_code_push/shorebird_code_push.dart';

import '../config/env.dart';
import '../logging.dart';
import '../platform/platform_info.dart';

/// 热更新结果。
sealed class UpdateResult {
  const UpdateResult();
}

/// 已是最新。
class UpToDate extends UpdateResult {
  const UpToDate();
}

/// 移动端：Shorebird 补丁已下载，下次冷启动生效。
class PatchReady extends UpdateResult {
  const PatchReady();
}

/// 桌面端：检测到新安装包，需引导用户下载（Shorebird 不支持桌面热更 Dart）。
class DesktopUpdateAvailable extends UpdateResult {
  const DesktopUpdateAvailable({
    required this.version,
    required this.downloadUrl,
    this.notes,
  });
  final String version;
  final String downloadUrl;
  final String? notes;
}

/// 检查失败/不可用（静默忽略，不打扰用户）。
class UpdateUnavailable extends UpdateResult {
  const UpdateUnavailable(this.reason);
  final String reason;
}

/// 统一热更新入口（D：Shorebird code push + 桌面端配置 OTA）。
///
/// - Android / iOS：通过 Shorebird 检查并静默下载 Dart 补丁，下次启动生效
/// - Windows / macOS：拉取版本清单 JSON，比较 build 号，提示下载新安装包
class UpdateService {
  UpdateService({Dio? dio}) : _dio = dio ?? Dio();
  // TODO(im-app): Remote Config 服务端确定后，把桌面端 manifest 签名校验和
  // 灰度策略放到这里，避免被劫持的下载地址直接进入安装引导。

  final Dio _dio;
  final _log = appLogger('update');
  final ShorebirdUpdater _updater = ShorebirdUpdater();

  Future<UpdateResult> check() async {
    if (PlatformInfo.supportsShorebird) {
      return _checkShorebird();
    }
    return _checkDesktopManifest();
  }

  // ── 移动端：Shorebird ────────────────────────────────────
  Future<UpdateResult> _checkShorebird() async {
    try {
      final status = await _updater.checkForUpdate();
      if (status == UpdateStatus.upToDate) return const UpToDate();
      if (status == UpdateStatus.restartRequired) return const PatchReady();
      if (status == UpdateStatus.outdated) {
        // 静默下载补丁；shorebird.yaml auto_update=true 时引擎也会自动拉，
        // 这里主动触发可更快就绪。
        await _updater.update();
        return const PatchReady();
      }
      return const UpdateUnavailable('shorebird unavailable');
    } catch (e) {
      _log.fine('shorebird check skipped: $e');
      return UpdateUnavailable('$e');
    }
  }

  // ── 桌面端：版本清单 OTA ─────────────────────────────────
  Future<UpdateResult> _checkDesktopManifest() async {
    const url = Env.desktopUpdateManifestUrl;
    if (url.isEmpty) return const UpdateUnavailable('no desktop manifest url');
    try {
      final info = await PackageInfo.fromPlatform();
      final currentBuild = int.tryParse(info.buildNumber) ?? 0;

      final resp = await _dio.get<Map<String, dynamic>>(url);
      final data = resp.data ?? const {};
      // 清单：{ "version": "0.2.0", "build": 5,
      //         "platforms": { "windows": {"url": "..."}, "macos": {"url": "..."} },
      //         "notes": "..." }
      final remoteBuild = (data['build'] as num?)?.toInt() ?? 0;
      final platforms = (data['platforms'] as Map?) ?? const {};
      final key =
          PlatformInfo.current == AppPlatform.windows ? 'windows' : 'macos';
      final platform = (platforms[key] as Map?) ?? const {};
      final downloadUrl = (platform['url'] ?? '') as String;

      if (remoteBuild > currentBuild && downloadUrl.isNotEmpty) {
        return DesktopUpdateAvailable(
          version: (data['version'] ?? '').toString(),
          downloadUrl: downloadUrl,
          notes: data['notes'] as String?,
        );
      }
      return const UpToDate();
    } catch (e) {
      _log.fine('desktop manifest check failed: $e');
      return UpdateUnavailable('$e');
    }
  }

  /// 当前已生效的 Shorebird 补丁号（移动端展示用，桌面端返回 null）。
  Future<int?> currentPatchNumber() async {
    if (!PlatformInfo.supportsShorebird) return null;
    try {
      final patch = await _updater.readCurrentPatch();
      return patch?.number;
    } catch (_) {
      return null;
    }
  }
}
