import 'package:flutter/widgets.dart';

import 'core/logging.dart';

/// 应用启动前的统一初始化入口。
///
/// 后续接入 crash reporting、remote config、Shorebird 手动检查、数据库预热时，
/// 都放在这里，不要散落到 UI 页面。
Future<void> bootstrap() async {
  WidgetsFlutterBinding.ensureInitialized();
  initLogging();
}
