import 'package:drift/drift.dart';

import '../app_database.dart';
import '../tables.dart';

part 'sync_cursor_dao.g.dart';

/// 同步游标本地数据源。
///
/// - `global:conv_list` 保存会话列表版本，用于 SYNC_REQ 的全局增量。
/// - `conv:<convId>` 保存单会话连续同步 seq，便于重连、全量修复和问题排查。
@DriftAccessor(tables: [SyncCursors])
class SyncCursorDao extends DatabaseAccessor<AppDatabase>
    with _$SyncCursorDaoMixin {
  SyncCursorDao(super.db);

  static const String globalConvListKey = 'global:conv_list';

  Future<String?> getGlobalConvListVersion() async {
    final row = await (select(syncCursors)
          ..where((t) => t.cursorKey.equals(globalConvListKey)))
        .getSingleOrNull();
    return row?.convListVersion;
  }

  Future<void> setGlobalConvListVersion(String version) {
    return into(syncCursors).insertOnConflictUpdate(
      SyncCursorsCompanion(
        cursorKey: const Value(globalConvListKey),
        convListVersion: Value(version),
        updatedAtMs: Value(DateTime.now().millisecondsSinceEpoch),
      ),
    );
  }

  Future<void> upsertConversationCursor({
    required String convId,
    required String localSeq,
    String? serverMaxSeq,
  }) {
    return into(syncCursors).insertOnConflictUpdate(
      SyncCursorsCompanion(
        cursorKey: Value('conv:$convId'),
        convId: Value(convId),
        localSeq: Value(localSeq),
        serverMaxSeq: Value(serverMaxSeq),
        updatedAtMs: Value(DateTime.now().millisecondsSinceEpoch),
      ),
    );
  }
}
