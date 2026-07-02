import '../im_engine.dart';
import '../local/daos/message_dao.dart';
import '../models/chat_message.dart';
import '../models/message_content.dart';

/// 消息仓储：UI 唯一的消息读写入口（验收要求 #4、#5）。
///
/// 读：直接订阅本地 DB 的 Stream（离线可读）。
/// 写：委托 ImEngine（乐观入库 + Outbox + 网关发送）。
/// UI 不感知 WebSocket / DAO / Engine 的存在。
class MessageRepository {
  MessageRepository(this._engine, this._messageDao);

  final ImEngine _engine;
  final MessageDao _messageDao;

  Stream<List<ChatMessage>> watch(String convId) =>
      _messageDao.watchMessages(convId);

  Future<void> sendText(String convId, String text,
          {List<String> atUserIds = const []}) =>
      _engine.sendText(convId, text, atUserIds: atUserIds);
  Future<void> sendImage(String convId, ImageBody image) =>
      _engine.sendImage(convId, image);
  Future<void> sendFile(String convId, FileBody file) =>
      _engine.sendFile(convId, file);
  Future<void> sendVoice(String convId, VoiceBody voice) =>
      _engine.sendVoice(convId, voice);

  Future<void> retry(String convId, String clientMsgId) =>
      _engine.retry(convId, clientMsgId);
  Future<void> loadOlder(String convId) => _engine.loadOlder(convId);
  Future<void> markRead(String convId, String readSeq) =>
      _engine.markRead(convId, readSeq);
}
