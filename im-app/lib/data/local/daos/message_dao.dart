import 'package:drift/drift.dart';

import '../../models/chat_message.dart';
import '../../models/enums.dart';
import '../../models/message_content.dart';
import '../app_database.dart';
import '../content_json.dart';
import '../db_mappers.dart';
import '../tables.dart';

part 'message_dao.g.dart';

@DriftAccessor(tables: [Messages])
class MessageDao extends DatabaseAccessor<AppDatabase> with _$MessageDaoMixin {
  MessageDao(super.db);

  /// 某会话的消息流，供聊天页响应式订阅。
  /// 排序：已确认消息（有 seqInt）按 seq 升序在前；未确认（sending/failed，seqInt 为空）
  /// 按 sendTime 升序排在最后——与微信「发送中气泡始终在底部」体验一致。
  Stream<List<ChatMessage>> watchMessages(String convId, {int limit = 500}) {
    final query = select(messages)
      ..where((t) => t.convId.equals(convId))
      ..orderBy([
        (t) => OrderingTerm(expression: t.seqInt.isNull()), // 有 seq 的在前
        (t) => OrderingTerm(expression: t.seqInt),
        (t) => OrderingTerm(expression: t.sendTimeMs),
      ])
      ..limit(limit);
    return query.watch().map((rows) => rows.map((r) => r.toModel()).toList());
  }

  /// upsert 单条（clientMsgId 主键去重——多端同步/自回显天然幂等）。
  Future<void> upsert(ChatMessage msg) =>
      into(messages).insertOnConflictUpdate(msg.toCompanion());

  /// 批量 upsert（同步增量 / 历史分页）。单事务提交。
  Future<void> upsertAll(List<ChatMessage> msgs) async {
    if (msgs.isEmpty) return;
    await batch((b) {
      for (final m in msgs) {
        b.insert(messages, m.toCompanion(),
            onConflict: DoUpdate((_) => m.toCompanion()));
      }
    });
  }

  /// 收到 MSG_SEND_ACK 后回填 server_msg_id / seq / 状态。
  Future<void> updateByClientMsgId(
    String clientMsgId, {
    String? convId,
    String? serverMsgId,
    String? seq,
    MessageStatus? status,
    int? failCode,
    bool clearFailCode = false,
  }) {
    final companion = MessagesCompanion(
      convId: convId == null ? const Value.absent() : Value(convId),
      serverMsgId:
          serverMsgId == null ? const Value.absent() : Value(serverMsgId),
      seq: seq == null ? const Value.absent() : Value(seq),
      seqInt: seq == null ? const Value.absent() : Value(int.tryParse(seq)),
      status: status == null ? const Value.absent() : Value(status.index),
      failCode: clearFailCode
          ? const Value(null)
          : (failCode == null ? const Value.absent() : Value(failCode)),
    );
    return (update(messages)..where((t) => t.clientMsgId.equals(clientMsgId)))
        .write(companion);
  }

  /// 撤回：状态置 revoked，内容替换为灰条提示（与 im-web revokeMessage 一致）。
  Future<void> revokeBySeq(String convId, String seq) {
    final companion = MessagesCompanion(
      status: Value(MessageStatus.revoked.index),
      contentJson: Value(
        ContentJson.encode(
            const NotificationBody(eventType: 'message.revoked')),
      ),
    );
    return (update(messages)
          ..where((t) => t.convId.equals(convId) & t.seq.equals(seq)))
        .write(companion);
  }

  /// 历史分页：取 seqInt < beforeSeqInt 的更早消息（倒序取 limit 条）。
  Future<List<ChatMessage>> pageBefore(
    String convId,
    int? beforeSeqInt, {
    int limit = 20,
  }) async {
    final query = select(messages)
      ..where((t) {
        final base = t.convId.equals(convId) & t.seqInt.isNotNull();
        return beforeSeqInt == null
            ? base
            : base & t.seqInt.isSmallerThanValue(beforeSeqInt);
      })
      ..orderBy(
          [(t) => OrderingTerm(expression: t.seqInt, mode: OrderingMode.desc)])
      ..limit(limit);
    final rows = await query.get();
    return rows.reversed.map((r) => r.toModel()).toList();
  }

  Future<ChatMessage?> getByClientMsgId(String clientMsgId) async {
    final row = await (select(messages)
          ..where((t) => t.clientMsgId.equals(clientMsgId)))
        .getSingleOrNull();
    return row?.toModel();
  }

  Future<List<ChatMessage>> searchMessages(
    String keyword, {
    int limit = 80,
  }) async {
    final queryText = _ftsQuery(keyword);
    if (queryText.isEmpty) return const [];
    final rows = await customSelect(
      '''
      SELECT m.*
      FROM messages m
      JOIN message_search_fts f ON f.client_msg_id = m.client_msg_id
      WHERE message_search_fts MATCH ?
      ORDER BY m.send_time_ms DESC
      LIMIT ?
      ''',
      variables: [Variable<String>(queryText), Variable<int>(limit)],
      readsFrom: {messages},
    ).get();
    return rows.map((row) => messages.map(row.data).toModel()).toList();
  }

  /// 已确认消息的 seq 列表（计算连续 syncSeq 用）。
  Future<List<String>> getSeqs(String convId) async {
    final query = selectOnly(messages)
      ..addColumns([messages.seq])
      ..where(messages.convId.equals(convId) & messages.seq.isNotNull());
    final rows = await query.get();
    return rows.map((r) => r.read(messages.seq)!).toList();
  }

  Future<void> clearConv(String convId) =>
      (delete(messages)..where((t) => t.convId.equals(convId))).go();

  Future<void> clearAll() => delete(messages).go();

  String _ftsQuery(String raw) {
    final tokens = raw
        .trim()
        .split(RegExp(r'\s+'))
        .map((token) => token.trim())
        .where((token) => token.isNotEmpty)
        .map((token) => '"${token.replaceAll('"', '""')}"')
        .toList();
    return tokens.join(' ');
  }
}
