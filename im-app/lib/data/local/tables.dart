import 'package:drift/drift.dart';

/// 用户缓存表。用于会话标题、头像、官方认证、多端资料同步等本地展示。
@DataClassName('UserRow')
class Users extends Table {
  TextColumn get userId => text()();
  TextColumn get tenantId => text().withDefault(const Constant('1'))();
  TextColumn get account => text().nullable()();
  TextColumn get nickname => text().nullable()();
  TextColumn get username => text().nullable()();
  TextColumn get avatar => text().nullable()();
  IntColumn get verifiedType => integer().withDefault(const Constant(0))();
  IntColumn get userType => integer().withDefault(const Constant(0))();
  IntColumn get updatedAtMs => integer().withDefault(const Constant(0))();

  @override
  Set<Column> get primaryKey => {userId};
}

/// 群组缓存表。MVP 先承载列表展示和群资料快照，成员表二阶段按需拆出。
@DataClassName('GroupRow')
class Groups extends Table {
  TextColumn get groupId => text()();
  TextColumn get tenantId => text().withDefault(const Constant('1'))();
  TextColumn get name => text().withDefault(const Constant(''))();
  TextColumn get avatar => text().nullable()();
  TextColumn get ownerUserId => text().nullable()();
  IntColumn get memberCount => integer().withDefault(const Constant(0))();
  IntColumn get maxMemberCount => integer().withDefault(const Constant(500))();
  IntColumn get updatedAtMs => integer().withDefault(const Constant(0))();

  @override
  Set<Column> get primaryKey => {groupId};
}

/// 会话表。id/seq 用 TEXT 存十进制串保精度；排序/未读用整型辅助列。
@DataClassName('ConversationRow')
class Conversations extends Table {
  TextColumn get convId => text()();
  IntColumn get type => integer().withDefault(const Constant(1))();
  TextColumn get title => text().withDefault(const Constant(''))();
  TextColumn get avatar => text().nullable()();
  TextColumn get peerUserId => text().nullable()();
  TextColumn get groupId => text().nullable()();
  TextColumn get maxSeq => text().withDefault(const Constant('0'))();

  /// 本端已连续同步到的 seq（Sync Cursor 的会话级分量，核心约定 4）。
  TextColumn get syncSeq => text().nullable()();
  TextColumn get readSeq => text().withDefault(const Constant('0'))();
  TextColumn get peerReadSeq => text().nullable()();
  BoolColumn get pinned => boolean().withDefault(const Constant(false))();
  BoolColumn get muted => boolean().withDefault(const Constant(false))();
  TextColumn get lastMsgAbstract => text().withDefault(const Constant(''))();

  /// 排序用：最后消息时间（ms epoch）。
  IntColumn get lastMsgTimeMs => integer().nullable()();
  TextColumn get csStatus => text().nullable()();

  /// 草稿（验收要求 #13）：离开会话未发送的文本，下次进入恢复。
  TextColumn get draft => text().nullable()();

  @override
  Set<Column> get primaryKey => {convId};
}

/// 消息表。clientMsgId 为稳定主键（乐观阶段即存在）。
/// 去重：clientMsgId 主键 + (convId, seq) 唯一索引（见 app_database 迁移），
/// 保证重连/重复 SYNC 不会插入重复消息（验收要求 #9）。
@DataClassName('MessageRow')
class Messages extends Table {
  TextColumn get clientMsgId => text()();
  TextColumn get convId => text()();
  TextColumn get serverMsgId => text().nullable()();

  /// 会话级 seq（十进制串，可空——pending/sending/failed 尚无 seq）。
  TextColumn get seq => text().nullable()();

  /// 排序辅助：seq 的整型值。为空（未确认消息）时排在已确认消息之后。
  IntColumn get seqInt => integer().nullable()();

  TextColumn get senderId => text().withDefault(const Constant('0'))();
  TextColumn get senderNickname => text().withDefault(const Constant(''))();
  TextColumn get senderAvatar => text().nullable()();
  IntColumn get senderVerifiedType =>
      integer().withDefault(const Constant(0))();
  IntColumn get senderUserType => integer().withDefault(const Constant(0))();

  /// MessageContent 的 JSON（含 kind）。
  TextColumn get contentJson => text()();
  IntColumn get sendTimeMs => integer().withDefault(const Constant(0))();

  /// MessageStatus.index
  IntColumn get status => integer().withDefault(const Constant(2))(); // 默认 sent
  IntColumn get failCode => integer().nullable()();

  @override
  Set<Column> get primaryKey => {clientMsgId};
}

/// 消息附件表。图片、语音、文件、视频都以附件快照承载，便于后续上传、下载、
/// 缩略图、转码状态独立演进，不把所有富媒体状态塞进 messages.contentJson。
/// TODO(im-app): 文件直传接入后补 upload_session_id、checksum 和 remote_url 缓存列。
@DataClassName('MessageAttachmentRow')
class MessageAttachments extends Table {
  TextColumn get attachmentId => text()();
  TextColumn get clientMsgId => text()();
  TextColumn get convId => text()();
  TextColumn get serverMsgId => text().nullable()();
  TextColumn get kind => text()(); // image / voice / file / video
  TextColumn get objectKey => text().nullable()();
  TextColumn get thumbKey => text().nullable()();
  TextColumn get localPath => text().nullable()();
  TextColumn get fileName => text().nullable()();
  TextColumn get mime => text().nullable()();
  IntColumn get size => integer().nullable()();
  IntColumn get width => integer().nullable()();
  IntColumn get height => integer().nullable()();
  IntColumn get durationMs => integer().nullable()();

  /// local / uploading / uploaded / failed。正式上传链路接入前先保留状态位。
  TextColumn get status => text().withDefault(const Constant('local'))();
  IntColumn get createdAtMs => integer().withDefault(const Constant(0))();

  @override
  Set<Column> get primaryKey => {attachmentId};
}

/// 同步游标表。全局会话列表版本和会话级连续 seq 都可落在这里。
/// conversations.syncSeq 仍作为热路径字段保留，sync_cursors 是更明确的同步边界。
@DataClassName('SyncCursorRow')
class SyncCursors extends Table {
  TextColumn get cursorKey => text()(); // global:conv_list / conv:<convId>
  TextColumn get convId => text().nullable()();
  TextColumn get localSeq => text().withDefault(const Constant('0'))();
  TextColumn get serverMaxSeq => text().nullable()();
  TextColumn get convListVersion => text().nullable()();
  IntColumn get updatedAtMs => integer().withDefault(const Constant(0))();

  @override
  Set<Column> get primaryKey => {cursorKey};
}

/// 发送 Outbox（验收要求 #8）：持久化待发消息，断网/重启后恢复发送。
/// 与 messages 表解耦——messages 是「展示态」，outbox 是「投递任务」。
@DataClassName('OutboxMessageRow')
class OutboxMessages extends Table {
  TextColumn get clientMsgId => text()(); // 与 message 同 id
  TextColumn get convId => text()();

  /// 预构造好的 MSG_SEND body（protobuf bytes），重发时直接投递，无需重建。
  BlobColumn get frameBody => blob()();
  IntColumn get createdAtMs => integer()();
  IntColumn get attempts => integer().withDefault(const Constant(0))();
  IntColumn get nextRetryAtMs => integer().nullable()();
  TextColumn get lastError => text().nullable()();

  @override
  Set<Column> get primaryKey => {clientMsgId};
}

/// 通用 KV（Sync Cursor 全局分量 conv_list_version、设备级标志等，验收要求 #10）。
@DataClassName('KvRow')
class AppKv extends Table {
  TextColumn get k => text()();
  TextColumn get v => text()();
  @override
  Set<Column> get primaryKey => {k};
}
