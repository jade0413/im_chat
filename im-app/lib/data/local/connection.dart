import 'dart:io';
import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sqlite3_flutter_libs/sqlite3_flutter_libs.dart';

/// 四端一致的本地 SQLite 连接（Android / iOS / macOS / Windows）。
/// 在后台 isolate 打开，避免大量消息读写阻塞 UI。
QueryExecutor openImDatabase() {
  return LazyDatabase(() async {
    final dir = await getApplicationSupportDirectory();
    final file = File(p.join(dir.path, 'lumo_im.sqlite'));

    // 老版本 Android 需要修复打开 sqlite3 的兼容问题
    if (Platform.isAndroid) {
      await applyWorkaroundToOpenSqlite3OnOldAndroidVersions();
    }

    return NativeDatabase.createInBackground(
      file,
      setup: (db) {
        db.execute('PRAGMA foreign_keys = ON;');
        db.execute('PRAGMA journal_mode = WAL;'); // 提升并发读写
      },
    );
  });
}
