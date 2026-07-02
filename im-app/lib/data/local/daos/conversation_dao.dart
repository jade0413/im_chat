import 'package:drift/drift.dart';

import '../../models/conversation.dart';
import '../app_database.dart';
import '../db_mappers.dart';
import '../tables.dart';

part 'conversation_dao.g.dart';

@DriftAccessor(tables: [Conversations])
class ConversationDao extends DatabaseAccessor<AppDatabase>
    with _$ConversationDaoMixin {
  ConversationDao(super.db);

  /// 会话列表流：置顶优先，其次按最后消息时间倒序。UI 直接订阅。
  Stream<List<Conversation>> watchConversations() {
    final query = select(conversations)
      ..orderBy([
        (t) => OrderingTerm(expression: t.pinned, mode: OrderingMode.desc),
        (t) =>
            OrderingTerm(expression: t.lastMsgTimeMs, mode: OrderingMode.desc),
      ]);
    return query.watch().map((rows) => rows.map((r) => r.toModel()).toList());
  }

  Future<List<Conversation>> getAllConversations() async {
    final rows = await select(conversations).get();
    return rows.map((r) => r.toModel()).toList();
  }

  Future<Conversation?> getConversation(String convId) async {
    final row = await (select(conversations)
          ..where((t) => t.convId.equals(convId)))
        .getSingleOrNull();
    return row?.toModel();
  }

  Stream<Conversation?> watchConversation(String convId) {
    return (select(conversations)..where((t) => t.convId.equals(convId)))
        .watchSingleOrNull()
        .map((r) => r?.toModel());
  }

  /// upsert（按 convId 主键）。整对象覆盖——调用方负责合并增量字段。
  Future<void> upsertConversation(Conversation conv) =>
      into(conversations).insertOnConflictUpdate(conv.toCompanion());

  Future<void> removeConversation(String convId) =>
      (delete(conversations)..where((t) => t.convId.equals(convId))).go();

  /// 局部更新本端已读位置（标记已读时调用，避免整对象覆盖竞态）。
  Future<void> updateReadSeq(String convId, String readSeq) =>
      (update(conversations)..where((t) => t.convId.equals(convId)))
          .write(ConversationsCompanion(readSeq: Value(readSeq)));

  Future<void> updatePeerReadSeq(String convId, String peerReadSeq) =>
      (update(conversations)..where((t) => t.convId.equals(convId)))
          .write(ConversationsCompanion(peerReadSeq: Value(peerReadSeq)));

  Future<void> updateSyncSeq(String convId, String syncSeq) =>
      (update(conversations)..where((t) => t.convId.equals(convId)))
          .write(ConversationsCompanion(syncSeq: Value(syncSeq)));

  /// 草稿（验收要求 #13）：离开会话时存，进入时取。空串视为清除。
  Future<void> updateDraft(String convId, String? draft) =>
      (update(conversations)..where((t) => t.convId.equals(convId))).write(
        ConversationsCompanion(
          draft: Value((draft == null || draft.isEmpty) ? null : draft),
        ),
      );

  Future<void> setPinned(String convId, bool pinned) =>
      (update(conversations)..where((t) => t.convId.equals(convId)))
          .write(ConversationsCompanion(pinned: Value(pinned)));

  Future<void> setMuted(String convId, bool muted) =>
      (update(conversations)..where((t) => t.convId.equals(convId)))
          .write(ConversationsCompanion(muted: Value(muted)));

  /// 会话标题（C2C 设置备注后本地即时反映；群改名同理）。
  Future<void> updateTitle(String convId, String title) =>
      (update(conversations)..where((t) => t.convId.equals(convId)))
          .write(ConversationsCompanion(title: Value(title)));

  Future<void> clearAll() => delete(conversations).go();
}
