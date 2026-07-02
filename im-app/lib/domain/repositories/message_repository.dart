import '../../data/models/message_content.dart';
import '../entities/message.dart';

abstract interface class MessageRepository {
  Stream<List<Message>> watch(String convId);
  Future<void> sendText(String convId, String text);
  Future<void> sendImage(String convId, ImageBody image);
  Future<void> sendVoice(String convId, VoiceBody voice);
  Future<void> sendFile(String convId, FileBody file);
  Future<void> retry(String convId, String clientMsgId);
  Future<void> markRead(String convId, String readSeq);
}
