import 'package:drift/drift.dart';

import '../app_database.dart';
import '../tables.dart';

part 'kv_dao.g.dart';

/// 通用 KV 数据源。承载 Sync Cursor 的全局分量 conv_list_version 等（验收要求 #10）。
@DriftAccessor(tables: [AppKv])
class KvDao extends DatabaseAccessor<AppDatabase> with _$KvDaoMixin {
  KvDao(super.db);

  static const String kConvListVersion = 'conv_list_version';

  Future<String?> get(String key) async {
    final row =
        await (select(appKv)..where((t) => t.k.equals(key))).getSingleOrNull();
    return row?.v;
  }

  Future<void> set(String key, String value) =>
      into(appKv).insertOnConflictUpdate(
        AppKvCompanion(k: Value(key), v: Value(value)),
      );
}
