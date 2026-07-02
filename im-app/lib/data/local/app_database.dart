import 'package:drift/drift.dart';

import 'connection.dart';
import 'daos/conversation_dao.dart';
import 'daos/kv_dao.dart';
import 'daos/message_dao.dart';
import 'daos/outbox_dao.dart';
import 'daos/sync_cursor_dao.dart';
import 'tables.dart';

part 'app_database.g.dart';

/// 本地缓存数据库（drift / SQLite）。四端一致。
///
/// 设计：消息/会话/Outbox/游标本地落库，UI 通过 DAO 的 Stream 响应式订阅；
/// 网络层只负责把数据写进 DB，UI 不直接依赖网络层——离线可读、重连同步即自动刷新界面。
@DriftDatabase(
  tables: [
    Users,
    Groups,
    Conversations,
    Messages,
    MessageAttachments,
    SyncCursors,
    OutboxMessages,
    AppKv,
  ],
  daos: [ConversationDao, MessageDao, OutboxDao, SyncCursorDao, KvDao],
)
class AppDatabase extends _$AppDatabase {
  AppDatabase() : super(openImDatabase());
  AppDatabase.forTesting(super.executor);

  @override
  int get schemaVersion => 2;

  @override
  MigrationStrategy get migration => MigrationStrategy(
        onCreate: (m) async {
          await m.createAll();
          await _createIndexes();
        },
        onUpgrade: (m, from, to) async {
          if (from < 2) {
            await m.createTable(users);
            await m.createTable(groups);
            await m.createTable(messageAttachments);
            await m.createTable(syncCursors);
            await m.createTable(outboxMessages);
            await customStatement(
              '''
              INSERT OR IGNORE INTO outbox_messages
                (client_msg_id, conv_id, frame_body, created_at_ms, attempts)
              SELECT client_msg_id, conv_id, frame_body, created_at_ms, attempts
              FROM outbox
              ''',
            );
          }
          await _createIndexes();
        },
      );

  Future<void> _createIndexes() async {
    // 排序/查询索引
    await customStatement(
      'CREATE INDEX IF NOT EXISTS idx_msg_conv_seq ON messages (conv_id, seq_int);',
    );
    await customStatement(
      'CREATE INDEX IF NOT EXISTS idx_conv_order ON conversations (pinned, last_msg_time_ms);',
    );
    await customStatement(
      'CREATE INDEX IF NOT EXISTS idx_attachment_msg ON message_attachments (client_msg_id);',
    );
    await customStatement(
      'CREATE INDEX IF NOT EXISTS idx_sync_cursor_conv ON sync_cursors (conv_id);',
    );
    await customStatement(
      'CREATE INDEX IF NOT EXISTS idx_outbox_retry ON outbox_messages (created_at_ms, next_retry_at_ms);',
    );
    // 去重：同一会话同一 seq 至多一条（防重连/重复 SYNC 插入重复）
    await customStatement(
      'CREATE UNIQUE INDEX IF NOT EXISTS uq_msg_conv_seq ON messages (conv_id, seq) WHERE seq IS NOT NULL;',
    );
  }
}
