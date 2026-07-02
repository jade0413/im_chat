import 'package:drift/drift.dart';

import '../app_database.dart';
import '../tables.dart';

part 'outbox_dao.g.dart';

/// 发送 Outbox 的本地数据源（验收要求 #8）。
/// 投递任务的唯一真相；重连/重启后由 ImEngine 读取并恢复发送。
@DriftAccessor(tables: [OutboxMessages])
class OutboxDao extends DatabaseAccessor<AppDatabase> with _$OutboxDaoMixin {
  OutboxDao(super.db);

  Future<void> enqueue({
    required String clientMsgId,
    required String convId,
    required Uint8List frameBody,
  }) =>
      into(outboxMessages).insertOnConflictUpdate(
        OutboxMessagesCompanion(
          clientMsgId: Value(clientMsgId),
          convId: Value(convId),
          frameBody: Value(frameBody),
          createdAtMs: Value(DateTime.now().millisecondsSinceEpoch),
          attempts: const Value(0),
        ),
      );

  /// 全部待发任务，按创建时间升序（保证发送顺序）。
  Future<List<OutboxMessageRow>> pending() {
    return (select(outboxMessages)
          ..orderBy([(t) => OrderingTerm(expression: t.createdAtMs)]))
        .get();
  }

  Future<void> remove(String clientMsgId) =>
      (delete(outboxMessages)..where((t) => t.clientMsgId.equals(clientMsgId)))
          .go();

  Future<void> bumpAttempt(String clientMsgId) => customUpdate(
        'UPDATE outbox_messages SET attempts = attempts + 1 WHERE client_msg_id = ?',
        variables: [Variable<String>(clientMsgId)],
        updates: {outboxMessages},
      );

  Future<void> clear() => delete(outboxMessages).go();
}
