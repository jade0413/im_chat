import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/update/update_service.dart';

final updateServiceProvider = Provider<UpdateService>((ref) => UpdateService());

/// 启动时检查一次热更新；UI 监听结果，桌面端有新版则弹下载引导。
final startupUpdateCheckProvider = FutureProvider<UpdateResult>((ref) async {
  return ref.watch(updateServiceProvider).check();
});
