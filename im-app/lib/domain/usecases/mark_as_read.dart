import '../repositories/message_repository.dart';

class MarkAsRead {
  const MarkAsRead(this._messages);

  final MessageRepository _messages;

  Future<void> call(String convId, String readSeq) {
    return _messages.markRead(convId, readSeq);
  }
}
