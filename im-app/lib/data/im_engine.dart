import 'dart:async';
import 'dart:typed_data';

import 'package:fixnum/fixnum.dart';

import '../core/logging.dart';
import '../core/proto/codec.dart' as pb;
import '../core/utils/id.dart';
import '../core/utils/seq.dart';
import '../core/utils/uuid.dart';
import 'call/call_engine.dart';
import 'local/app_database.dart';
import 'local/daos/conversation_dao.dart';
import 'local/daos/kv_dao.dart';
import 'local/daos/message_dao.dart';
import 'local/daos/outbox_dao.dart';
import 'local/daos/sync_cursor_dao.dart';
import 'models/chat_message.dart';
import 'models/conversation.dart';
import 'models/enums.dart';
import 'models/message_content.dart';
import 'models/sender_info.dart';
import 'models/session_user.dart';
import 'remote/rest/api_client.dart';
import 'remote/rest/dto.dart';
import 'remote/rest/message_api.dart';
import 'remote/ws/im_socket.dart';
import 'remote/ws/ws_mappers.dart';

/// 消息引擎：连接层（ImSocket）与本地缓存（drift）之间的应用层协调者。
///
/// 职责：
/// - 下行帧分发 → 落本地 DB（UI 经 DAO Stream 自动刷新；离线可读、重连即同步）
/// - 上行发送：乐观入库 + 持久化 Outbox（断网/重启可恢复，验收 #8）
/// - seq 对齐增量同步（验收 #10）；(convId,seq) 去重幂等（验收 #9）
/// - ACK 流程：收消息回 MSG_RECV_ACK（验收 #12）
class ImEngine {
  ImEngine({
    required AppDatabase database,
    required ConversationDao conversationDao,
    required MessageDao messageDao,
    required OutboxDao outboxDao,
    required SyncCursorDao syncCursorDao,
    required KvDao kvDao,
    required MessageApi messageApi,
    required ApiClient apiClient,
    required SessionUser? Function() currentUser,
    required Future<void> Function() onAuthExpired,
    required void Function(pb.KickNotify kick) onKicked,
    required Future<String> Function() getDeviceId,
    required Future<String?> Function() getAccessToken,
  })  : _db = database,
        _convDao = conversationDao,
        _msgDao = messageDao,
        _outboxDao = outboxDao,
        _syncCursorDao = syncCursorDao,
        _kvDao = kvDao,
        _messageApi = messageApi,
        _currentUser = currentUser {
    _socket = ImSocket(
      ImSocketDelegate(
        getAccessToken: getAccessToken,
        getDeviceId: getDeviceId,
        refreshToken: apiClient.forceRefresh,
        onAuthExpired: onAuthExpired,
        onKicked: onKicked,
        onBusinessFrame: _handleBusinessFrame,
        buildSyncReqBody: _buildSyncReqBody,
        onAuthenticated: (_) => unawaited(_drainOutbox()),
      ),
    );
  }

  final AppDatabase _db;
  final ConversationDao _convDao;
  final MessageDao _msgDao;
  final OutboxDao _outboxDao;
  final SyncCursorDao _syncCursorDao;
  final KvDao _kvDao;
  final MessageApi _messageApi;
  final SessionUser? Function() _currentUser;
  final _log = appLogger('engine');

  late final ImSocket _socket;
  ImSocket get socket => _socket;
  Stream<ConnectionState> get connectionState => _socket.stateStream;

  /// 通话引擎（D45）：providers 装配时注入，CALL_* 下行帧转交其处理。
  CallEngine? _callEngine;
  // ignore: use_setters_to_change_properties
  void bindCallEngine(CallEngine engine) => _callEngine = engine;

  /// Sync Cursor 全局分量（会话级分量是 conversation.syncSeq + sync_cursors）。
  String _convListVersion = '0';

  static const int _outboxMaxAgeMs = 7 * 24 * 3600 * 1000; // 7 天
  static const int _outboxMaxAttempts = 10;
  // TODO(im-app): 接入服务端送达/已读回执后，把 delivered/read 从会话
  // peerReadSeq 推导升级为消息级状态更新。

  // ─── 生命周期 ───────────────────────────────────────────

  Future<void> start() async {
    _convListVersion = await _syncCursorDao.getGlobalConvListVersion() ??
        await _kvDao.get(KvDao.kConvListVersion) ??
        '0';
    await _socket.connect();
  }

  Future<void> stop() => _socket.disconnect(manual: true);
  void onAppResumed() => _socket.onAppResumed();
  Future<void> dispose() => _socket.dispose();

  // ─── 上行：发送（乐观 + Outbox）─────────────────────────

  Future<void> sendText(String convId, String text,
          {List<String> atUserIds = const []}) =>
      _send(convId, TextBody(text, atUserIds: atUserIds));
  Future<void> sendImage(String convId, ImageBody image) =>
      _send(convId, image);
  Future<void> sendFile(String convId, FileBody file) => _send(convId, file);
  Future<void> sendVoice(String convId, VoiceBody voice) =>
      _send(convId, voice);

  Future<void> _send(String convId, MessageContent content) async {
    final user = _currentUser();
    final clientMsgId = createUuid();
    final now = DateTime.now().millisecondsSinceEpoch.toString();
    final optimistic = ChatMessage(
      clientMsgId: clientMsgId,
      convId: convId,
      sender: SenderInfo(
        userId: user?.id ?? '0',
        nickname: user?.displayName ?? '我',
        avatar: user?.avatar,
      ),
      content: content,
      sendTime: now,
      status: MessageStatus.pending,
    );
    final body = WsMappers.buildMsgSend(
      clientMsgId: clientMsgId,
      content: content,
      convId: convId,
    );
    // 先入 Outbox（持久化），再尝试即时发送
    await _db.transaction(() async {
      await _msgDao.upsert(optimistic);
      await _bumpConvPreview(convId, content.abstract, now);
      await _outboxDao.enqueue(
        clientMsgId: clientMsgId,
        convId: convId,
        frameBody: body,
      );
    });
    final sent = _socket.send(pb.Cmd.MSG_SEND, body);
    await _msgDao.updateByClientMsgId(
      clientMsgId,
      status: sent ? MessageStatus.sending : MessageStatus.pending,
    );
  }

  /// 手动重试 failed 消息。
  Future<void> retry(String convId, String clientMsgId) async {
    final msg = await _msgDao.getByClientMsgId(clientMsgId);
    if (msg == null) return;
    final body = WsMappers.buildMsgSend(
      clientMsgId: clientMsgId,
      content: msg.content,
      convId: convId,
    );
    await _outboxDao.enqueue(
      clientMsgId: clientMsgId,
      convId: convId,
      frameBody: body,
    );
    final sent = _socket.send(pb.Cmd.MSG_SEND, body);
    await _msgDao.updateByClientMsgId(
      clientMsgId,
      status: sent ? MessageStatus.sending : MessageStatus.pending,
      clearFailCode: true,
    );
  }

  /// AUTH 成功后恢复发送：把 Outbox 里堆积的任务重投。
  Future<void> _drainOutbox() async {
    // TODO(im-app): 协议联调稳定后增加 ACK 超时扫描，避免连接不断但服务端
    // 未 ACK 时消息长期停留在 sending。
    final rows = await _outboxDao.pending();
    if (rows.isEmpty) return;
    final now = DateTime.now().millisecondsSinceEpoch;
    for (final row in rows) {
      final tooOld = now - row.createdAtMs > _outboxMaxAgeMs;
      final tooMany = row.attempts >= _outboxMaxAttempts;
      if (tooOld || tooMany) {
        await _db.transaction(() async {
          await _outboxDao.remove(row.clientMsgId);
          await _msgDao.updateByClientMsgId(
            row.clientMsgId,
            status: MessageStatus.failed,
          );
        });
        continue;
      }
      await _outboxDao.bumpAttempt(row.clientMsgId);
      final sent =
          _socket.send(pb.Cmd.MSG_SEND, Uint8List.fromList(row.frameBody));
      if (sent) {
        await _msgDao.updateByClientMsgId(
          row.clientMsgId,
          status: MessageStatus.sending,
        );
      } else {
        break; // 连接又断了，剩余留待下次 drain
      }
    }
  }

  /// 标记已读：更新本端 readSeq + 上报 READ_REPORT。
  Future<void> markRead(String convId, String readSeq) async {
    await _convDao.updateReadSeq(convId, readSeq);
    _socket.send(
      pb.Cmd.READ_REPORT,
      WsMappers.buildReadReport(convId, readSeq),
    );
  }

  /// 向上滚动加载更早历史（REST 分页，落库后 UI 自动刷新）。
  Future<void> loadOlder(String convId) async {
    final seqs = await _msgDao.getSeqs(convId);
    if (seqs.isEmpty) return;
    final earliest = seqs.reduce((a, b) => Ids.compare(a, b) < 0 ? a : b);
    final items =
        await _messageApi.history(convId, endSeq: earliest, limit: 20);
    final mapped = items.map(_historyItemToMessage).toList();
    if (mapped.isNotEmpty) await _msgDao.upsertAll(mapped);
  }

  // ─── 下行：帧分发 ───────────────────────────────────────

  void _handleBusinessFrame(pb.Frame frame) {
    // frame.body 通常已是 Uint8List（protobuf 生成物内部表示），避免无谓整帧拷贝。
    final rawBody = frame.body;
    final body =
        rawBody is Uint8List ? rawBody : Uint8List.fromList(rawBody);
    switch (frame.cmd) {
      case pb.Cmd.MSG_PUSH:
        unawaited(_handleMsgPush(body, frame.reqId));
      case pb.Cmd.MSG_SEND_ACK:
        unawaited(_handleMsgSendAck(body));
      case pb.Cmd.SYNC_RESP:
        unawaited(_handleSyncResp(body));
      case pb.Cmd.READ_NOTIFY:
        unawaited(_handleReadNotify(body));
        _ackPushIfNeeded(frame.reqId);
      case pb.Cmd.REVOKE_NOTIFY:
        unawaited(_handleRevokeNotify(body));
        _ackPushIfNeeded(frame.reqId);
      case pb.Cmd.CONV_NOTIFY:
        unawaited(_handleConvNotify(body));
        _ackPushIfNeeded(frame.reqId);
      case pb.Cmd.CALL_NOTIFY:
        // D45：通话信令推送（need_ack=true，必须回 ack，否则网关判半死链断连）
        _dispatchCallNotify(body);
        _ackPushIfNeeded(frame.reqId);
      case pb.Cmd.CALL_ACK:
        _dispatchCallAck(body); // CALL_* 上行的响应帧，不 ack
      case pb.Cmd.ERROR:
        _handleError(body);
      default:
        _log.fine('unhandled cmd=${frame.cmd}');
        // 未知推送帧同样遵守 D28：带非 0 req_id 即回 ack，
        // 服务端新增 need_ack 推送类型时旧客户端不至于被判半死链踢断。
        _ackPushIfNeeded(frame.reqId);
    }
  }

  /// D28 防御性泛化：任何服务端主动推送若带非 0 req_id（need_ack=true），
  /// 都必须回 MSG_RECV_ACK 回带同 req_id，否则网关 10s 判半死链断连。
  /// 协议现状仅 MSG_PUSH 是 need_ack（其 ack 在 _handleMsgPush 内带 conv/seq
  /// 回执）；其余推送帧回空 items——网关只认 req_id，Java OnPushAcked 对空
  /// items 无副作用。
  void _ackPushIfNeeded(Int64 reqId) {
    if (reqId == Int64.ZERO) return;
    _socket.send(
      pb.Cmd.MSG_RECV_ACK,
      WsMappers.buildRecvAck(const []),
      reqId: reqId,
    );
  }

  Future<void> _handleMsgPush(Uint8List body, Int64 reqId) async {
    final push = pb.MsgPush.fromBuffer(body);
    final msg = WsMappers.msgPushToChatMessage(push);
    final convId = msg.convId;
    final pushSeq = Ids.toStr(push.seq);

    final existing = await _convDao.getConversation(convId);
    final currentSync =
        Seqs.resolveLocal(existing?.syncSeq, await _msgDao.getSeqs(convId));
    final hasGap = Seqs.hasGap(currentSync, pushSeq);

    late final String nextSync;
    await _db.transaction(() async {
      await _msgDao.upsert(msg); // (convId,seq)/clientMsgId 去重，幂等
      nextSync = Seqs.contiguousFrom(
        await _msgDao.getSeqs(convId),
        baseSeq: currentSync,
      );

      final nextConv = (existing ??
              Conversation(
                convId: convId,
                type: push.convType.value,
                title: msg.sender.nickname,
                avatar: msg.sender.avatar,
                maxSeq: pushSeq,
                readSeq: '0',
              ))
          .copyWith(
        maxSeq: Seqs.max(existing?.maxSeq, pushSeq),
        syncSeq: nextSync,
        readSeq: _readSeqAfterMessage(existing?.readSeq, msg, pushSeq),
        lastMsgAbstract: msg.content.abstract,
        lastMsgTime: Ids.toStr(push.sendTime),
      );
      await _convDao.upsertConversation(nextConv);
      await _syncCursorDao.upsertConversationCursor(
        convId: convId,
        localSeq: nextSync,
        serverMaxSeq: nextConv.maxSeq,
      );
    });

    if (hasGap) await _socket.sendSyncReq(); // 缺口→增量补齐

    // ACK 推送送达（验收 #12；D28：回带网关分配的 req_id）
    _socket.send(
      pb.Cmd.MSG_RECV_ACK,
      WsMappers.buildRecvAck([(convId: convId, seq: pushSeq)]),
      reqId: reqId == Int64.ZERO ? null : reqId,
    );
  }

  Future<void> _handleMsgSendAck(Uint8List body) async {
    final ack = pb.MsgSendAck.fromBuffer(body);
    final ackConvId = Ids.toStr(ack.convId);
    final convId = ackConvId != '0' ? ackConvId : null;

    if (ack.code != 0) {
      await _db.transaction(() async {
        await _outboxDao.remove(ack.clientMsgId);
        await _msgDao.updateByClientMsgId(
          ack.clientMsgId,
          status: MessageStatus.failed,
          failCode: ack.code,
        );
      });
      return;
    }
    if (convId == null) {
      await _outboxDao.remove(ack.clientMsgId);
      return;
    }
    final seq = Ids.toStr(ack.seq);
    await _db.transaction(() async {
      await _outboxDao.remove(ack.clientMsgId);
      await _msgDao.updateByClientMsgId(
        ack.clientMsgId,
        convId: convId,
        serverMsgId: Ids.toStr(ack.serverMsgId),
        seq: seq,
        status: MessageStatus.sent,
        clearFailCode: true,
      );
      final existing = await _convDao.getConversation(convId);
      final nextSync = Seqs.contiguousFrom(
        await _msgDao.getSeqs(convId),
        baseSeq: existing?.syncSeq ?? '0',
      );
      if (existing != null) {
        final nextConv = existing.copyWith(
          maxSeq: Seqs.max(existing.maxSeq, seq),
          syncSeq: nextSync,
          readSeq: Seqs.max(existing.readSeq, seq),
          lastMsgTime: Ids.toStr(ack.serverTime),
        );
        await _convDao.upsertConversation(nextConv);
        await _syncCursorDao.upsertConversationCursor(
          convId: convId,
          localSeq: nextSync,
          serverMaxSeq: nextConv.maxSeq,
        );
      }
    });
  }

  Future<void> _handleSyncResp(Uint8List body) async {
    final resp = pb.SyncResp.fromBuffer(body);
    if (resp.fullSync) {
      _log.info('full sync — clearing local messages');
      await _db.transaction(() async {
        await _msgDao.clearAll();
        for (final c in await _convDao.getAllConversations()) {
          await _convDao.updateSyncSeq(c.convId, '0');
          await _syncCursorDao.upsertConversationCursor(
            convId: c.convId,
            localSeq: '0',
            serverMaxSeq: c.maxSeq,
          );
        }
      });
    }
    _convListVersion = Ids.toStr(resp.convListVersion);
    await _db.transaction(() async {
      await _syncCursorDao.setGlobalConvListVersion(_convListVersion);
      await _kvDao.set(KvDao.kConvListVersion, _convListVersion);
    });

    var shouldContinue = false;
    for (final delta in resp.deltas) {
      if (!delta.hasConv()) continue;
      final conv = WsMappers.convInfoToConversation(delta.conv);
      if (delta.conv.deleted) {
        await _convDao.removeConversation(conv.convId);
        continue;
      }
      final before =
          (await _convDao.getConversation(conv.convId))?.syncSeq ?? '0';
      final msgs = delta.msgs.map(WsMappers.msgPushToChatMessage).toList();
      late final String nextSync;
      await _db.transaction(() async {
        if (msgs.isNotEmpty) await _msgDao.upsertAll(msgs);
        nextSync = Seqs.contiguousFrom(
          await _msgDao.getSeqs(conv.convId),
          baseSeq: before,
        );
        // 保留本地草稿/已读，仅覆盖服务端权威字段 + 推进 syncSeq
        final existing = await _convDao.getConversation(conv.convId);
        final merged = conv.copyWith(
          syncSeq: nextSync,
          readSeq: Seqs.max(existing?.readSeq, conv.readSeq),
          draft: existing?.draft,
        );
        await _convDao.upsertConversation(merged);
        await _syncCursorDao.upsertConversationCursor(
          convId: conv.convId,
          localSeq: nextSync,
          serverMaxSeq: conv.maxSeq,
        );
      });
      if (delta.hasMore && Ids.compare(nextSync, before) > 0) {
        shouldContinue = true;
      }
    }
    if (shouldContinue) unawaited(_socket.sendSyncReq());
  }

  Future<void> _handleReadNotify(Uint8List body) async {
    final notify = pb.ReadNotify.fromBuffer(body);
    final me = _currentUser()?.id ?? '0';
    if (Ids.toStr(notify.readerUserId) == me) return;
    await _convDao.updatePeerReadSeq(
      Ids.toStr(notify.convId),
      Ids.toStr(notify.readSeq),
    );
  }

  Future<void> _handleRevokeNotify(Uint8List body) async {
    final notify = pb.RevokeNotify.fromBuffer(body);
    await _msgDao.revokeBySeq(Ids.toStr(notify.convId), Ids.toStr(notify.seq));
  }

  Future<void> _handleConvNotify(Uint8List body) async {
    final notify = pb.ConvNotify.fromBuffer(body);
    if (!notify.hasConv()) return;
    final conv = WsMappers.convInfoToConversation(notify.conv);
    if (notify.changeType == 'removed') {
      await _convDao.removeConversation(conv.convId);
    } else {
      final existing = await _convDao.getConversation(conv.convId);
      await _convDao.upsertConversation(
        conv.copyWith(
          syncSeq: existing?.syncSeq,
          readSeq: Seqs.max(existing?.readSeq, conv.readSeq),
          draft: existing?.draft,
        ),
      );
    }
  }

  void _dispatchCallNotify(Uint8List body) {
    final engine = _callEngine;
    if (engine == null) {
      _log.warning('CALL_NOTIFY dropped: call engine not bound');
      return;
    }
    try {
      engine.onCallNotify(pb.CallNotify.fromBuffer(body));
    } catch (e) {
      _log.warning('call notify handling failed: $e');
    }
  }

  void _dispatchCallAck(Uint8List body) {
    try {
      _callEngine?.onCallAck(pb.CallAck.fromBuffer(body));
    } catch (e) {
      _log.warning('call ack handling failed: $e');
    }
  }

  void _handleError(Uint8List body) {
    final err = pb.ErrorBody.fromBuffer(body);
    _log.warning('server ERROR code=${err.code} msg=${err.message}');
  }

  String _readSeqAfterMessage(
    String? currentReadSeq,
    ChatMessage message,
    String messageSeq,
  ) {
    final me = _currentUser()?.id;
    if (me == null || message.sender.userId != me) return currentReadSeq ?? '0';
    return Seqs.max(currentReadSeq, messageSeq);
  }

  // ─── 工具 ───────────────────────────────────────────────

  Future<void> _bumpConvPreview(
    String convId,
    String abstract,
    String timeMs,
  ) async {
    final existing = await _convDao.getConversation(convId);
    if (existing == null) return; // 新会话由 ACK/PUSH 创建
    await _convDao.upsertConversation(
      existing.copyWith(
        lastMsgAbstract: abstract,
        lastMsgTime: timeMs,
      ),
    );
  }

  Future<Uint8List> _buildSyncReqBody() async {
    final convs = await _convDao.getAllConversations();
    final versions = <({String convId, String localMaxSeq})>[];
    for (final c in convs) {
      versions.add((
        convId: c.convId,
        localMaxSeq: Seqs.resolveLocal(
          c.syncSeq,
          await _msgDao.getSeqs(c.convId),
        ),
      ));
    }
    return WsMappers.buildSyncReq(
      convListVersion: _convListVersion,
      versions: versions,
    );
  }

  ChatMessage _historyItemToMessage(MessageItem item) {
    final revoked = item.status == 2;
    final MessageContent content;
    if (revoked) {
      content = const NotificationBody(eventType: 'message.revoked');
    } else if (item.objectKey != null && item.objectKey!.isNotEmpty) {
      final mime = item.mime ?? '';
      if (item.durationMs != null) {
        content = VoiceBody(
          objectKey: item.objectKey!,
          durationMs: item.durationMs!,
          size: item.size,
        );
      } else if (mime.startsWith('image/')) {
        content =
            ImageBody(objectKey: item.objectKey!, thumbKey: item.thumbKey);
      } else {
        content = FileBody(
          objectKey: item.objectKey!,
          fileName: item.fileName ?? '未命名文件',
          size: item.size,
          mime: item.mime,
        );
      }
    } else {
      content = switch (item.msgType) {
        1 => TextBody(item.text ?? ''),
        _ => const NotificationBody(eventType: 'message.unsupported'),
      };
    }
    return ChatMessage(
      clientMsgId: item.clientMsgId ?? '${item.convId}:${item.seq}',
      convId: item.convId,
      serverMsgId: item.serverMsgId,
      seq: item.seq,
      sender:
          SenderInfo(userId: item.senderId, nickname: '用户 ${item.senderId}'),
      content: content,
      sendTime: item.sendTime,
      status: revoked ? MessageStatus.revoked : MessageStatus.sent,
    );
  }
}
