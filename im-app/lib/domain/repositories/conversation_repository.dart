import '../entities/conversation.dart';

abstract interface class ConversationRepository {
  Stream<List<Conversation>> watchAll();
  Stream<Conversation?> watch(String convId);
  Future<Conversation?> get(String convId);
  Future<void> saveDraft(String convId, String? draft);
  Future<void> setPinned(String convId, bool pinned);
  Future<void> setMuted(String convId, bool muted);
}
