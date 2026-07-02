import 'package:flutter/foundation.dart';
import 'package:logging/logging.dart';

/// 统一日志入口。lint 禁用了 print，所有输出走这里。
/// 线上可在此对接崩溃/日志上报（二阶段）。
void initLogging({Level level = kDebugMode ? Level.FINE : Level.INFO}) {
  Logger.root.level = level;
  Logger.root.onRecord.listen((r) {
    if (kDebugMode) {
      // ignore: avoid_print
      print('${r.level.name.padRight(7)} ${r.time.toIso8601String()} '
          '[${r.loggerName}] ${r.message}'
          '${r.error != null ? '  ↳ ${r.error}' : ''}');
    }
  });
}

Logger appLogger(String name) => Logger(name);
