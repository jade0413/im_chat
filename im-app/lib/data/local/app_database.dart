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
  int get schemaVersion => 3;

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
          if (from < 3) {
            await _createMessageSearchIndex();
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
    await _createMessageSearchIndex();
  }

  Future<void> _createMessageSearchIndex() async {
    await customStatement(
      '''
      CREATE VIRTUAL TABLE IF NOT EXISTS message_search_fts USING fts5(
        client_msg_id UNINDEXED,
        conv_id UNINDEXED,
        sender,
        body,
        tokenize='unicode61'
      );
      ''',
    );
    await customStatement(
      '''
      INSERT OR IGNORE INTO message_search_fts(rowid, client_msg_id, conv_id, sender, body)
      SELECT rowid, client_msg_id, conv_id, sender_nickname, content_json
      FROM messages;
      ''',
    );
    await customStatement(
      '''
      CREATE TRIGGER IF NOT EXISTS message_search_ai
      AFTER INSERT ON messages BEGIN
        INSERT OR REPLACE INTO message_search_fts(rowid, client_msg_id, conv_id, sender, body)
        VALUES (new.rowid, new.client_msg_id, new.conv_id, new.sender_nickname, new.content_json);
      END;
      ''',
    );
    await customStatement(
      '''
      CREATE TRIGGER IF NOT EXISTS message_search_ad
      AFTER DELETE ON messages BEGIN
        DELETE FROM message_search_fts WHERE rowid = old.rowid;
      END;
      ''',
    );
    await customStatement(
      '''
      CREATE TRIGGER IF NOT EXISTS message_search_au
      AFTER UPDATE ON messages BEGIN
        DELETE FROM message_search_fts WHERE rowid = old.rowid;
        INSERT OR REPLACE INTO message_search_fts(rowid, client_msg_id, conv_id, sender, body)
        VALUES (new.rowid, new.client_msg_id, new.conv_id, new.sender_nickname, new.content_json);
      END;
      ''',
    );
  }
}
