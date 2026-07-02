import '../../data/models/message_content.dart';
import '../repositories/message_repository.dart';

class SendMessage {
  const SendMessage(this._messages);

  final MessageRepository _messages;

  Future<void> text(String convId, String text) {
    return _messages.sendText(convId, text);
  }

  Future<void> image(String convId, ImageBody image) {
    return _messages.sendImage(convId, image);
  }
}
