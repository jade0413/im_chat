import '../im_engine.dart';
import '../local/daos/message_dao.dart';
import '../media_cache_service.dart';
import '../models/chat_message.dart';
import '../models/message_content.dart';
import '../remote/rest/message_api.dart';

/// 消息仓储：UI 唯一的消息读写入口（验收要求 #4、#5）。
///
/// 读：直接订阅本地 DB 的 Stream（离线可读）。
/// 写：委托 ImEngine（乐观入库 + Outbox + 网关发送）。
/// UI 不感知 WebSocket / DAO / Engine 的存在。
class MessageRepository {
  MessageRepository(
    this._engine,
    this._messageDao,
    this._messageApi, {
    MediaCacheService? mediaCacheService,
  }) : _mediaCacheService = mediaCacheService;

  final ImEngine _engine;
  final MessageDao _messageDao;
  final MessageApi _messageApi;
  final MediaCacheService? _mediaCacheService;

  Stream<List<ChatMessage>> watch(String convId) =>
      _messageDao.watchMessages(convId);

  Future<List<ChatMessage>> search(String keyword, {int limit = 80}) =>
      _messageDao.searchMessages(keyword, limit: limit);

  Future<void> sendText(String convId, String text,
          {List<String> atUserIds = const []}) =>
      _engine.sendText(convId, text, atUserIds: atUserIds);
  Future<void> sendImage(String convId, ImageBody image) =>
      _engine.sendImage(convId, image);
  Future<void> sendFile(String convId, FileBody file) =>
      _engine.sendFile(convId, file);
  Future<void> sendVideo(String convId, VideoBody video) =>
      _engine.sendVideo(convId, video);
  Future<void> sendVoice(String convId, VoiceBody voice) =>
      _engine.sendVoice(convId, voice);
  Future<void> sendContent(String convId, MessageContent content) =>
      _engine.sendContent(convId, content);

  Future<void> retry(String convId, String clientMsgId) =>
      _engine.retry(convId, clientMsgId);
  Future<void> loadOlder(String convId) => _engine.loadOlder(convId);
  Future<void> markRead(String convId, String readSeq) =>
      _engine.markRead(convId, readSeq);
  Future<void> revoke(String convId, String seq) async {
    await _messageApi.revoke(convId, seq);
    await _messageDao.revokeBySeq(convId, seq);
  }

  Future<void> deleteLocal(String clientMsgId) =>
      _messageDao.deleteLocal(clientMsgId);
  Future<MediaCacheCleanupResult> clearLocal(String convId) async {
    final mediaKeys = _mediaCacheService == null
        ? const <String>[]
        : await _messageDao.mediaObjectKeysForConv(convId);
    await _messageDao.clearConv(convId);
    if (_mediaCacheService == null || mediaKeys.isEmpty) {
      return const MediaCacheCleanupResult(deletedBytes: 0, deletedFiles: 0);
    }
    return _mediaCacheService.evictObjectKeys(mediaKeys);
  }
}
