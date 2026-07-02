import '../../core/utils/seq.dart';
import '../local/daos/conversation_dao.dart';
import '../models/conversation.dart';
import '../remote/rest/conv_api.dart';

/// 会话仓储：UI 唯一的会话读写入口（验收要求 #4、#5、#13）。
class ConversationRepository {
  ConversationRepository(this._convDao, this._convApi);

  final ConversationDao _convDao;
  final ConvApi _convApi;

  Stream<List<Conversation>> watchAll() => _convDao.watchConversations();
  Stream<Conversation?> watch(String convId) =>
      _convDao.watchConversation(convId);
  Future<Conversation?> get(String convId) => _convDao.getConversation(convId);
  Future<void> save(Conversation conv) => _convDao.upsertConversation(conv);

  /// 打开/创建与某用户的单聊：调用 REST，确保本地会话存在，返回 convId。
  /// 之后聊天页通过 WS SYNC / REST 历史填充消息。
  Future<String> openC2c(
    String toUserId, {
    String? title,
    String? avatar,
  }) async {
    final remote = await _convApi.openC2c(toUserId);
    final existing = await _convDao.getConversation(remote.convId);
    final preferredTitle = _normalized(title);
    final preferredAvatar = _normalized(avatar);
    final nextTitle = _resolveTitle(
      existingTitle: existing?.title,
      remoteTitle: remote.title,
      preferredTitle: preferredTitle,
      peerUserId: toUserId,
    );
    await _convDao.upsertConversation(remote.copyWith(
      // 保留本地同步水位/草稿/已读，避免被一次性回退
      syncSeq: existing?.syncSeq,
      draft: existing?.draft,
      maxSeq: existing == null
          ? remote.maxSeq
          : Seqs.max(existing.maxSeq, remote.maxSeq),
      readSeq: existing?.readSeq ?? remote.readSeq,
      title: nextTitle,
      avatar: preferredAvatar ?? existing?.avatar ?? remote.avatar,
    ));
    return remote.convId;
  }

  String _resolveTitle({
    required String? existingTitle,
    required String remoteTitle,
    required String? preferredTitle,
    required String peerUserId,
  }) {
    final existing = _normalized(existingTitle);
    final remote = _normalizedTitle(remoteTitle, peerUserId);
    final preferred = _normalizedTitle(preferredTitle, peerUserId);
    final replacement = preferred ?? remote;

    if (existing == null) return replacement ?? remoteTitle;
    if (_looksLikeIdTitle(existing, peerUserId) && replacement != null) {
      return replacement;
    }
    if (existing == remoteTitle &&
        replacement != null &&
        replacement != remoteTitle) {
      return replacement;
    }
    return existing;
  }

  bool _looksLikeIdTitle(String title, String peerUserId) {
    return title == peerUserId ||
        RegExp(r'^\d{6,}$').hasMatch(title) ||
        title.startsWith('用户 ');
  }

  String? _normalizedTitle(String? value, String peerUserId) {
    final text = _normalized(value);
    if (text == null || _looksLikeIdTitle(text, peerUserId)) return null;
    return text;
  }

  String? _normalized(String? value) {
    final text = value?.trim();
    return text == null || text.isEmpty ? null : text;
  }

  // 会话列表交互（草稿/置顶/免打扰）
  Future<void> saveDraft(String convId, String? draft) =>
      _convDao.updateDraft(convId, draft);
  Future<void> setPinned(String convId, bool pinned) =>
      _convDao.setPinned(convId, pinned);
  Future<void> setMuted(String convId, bool muted) =>
      _convDao.setMuted(convId, muted);

  /// 本地重命名会话标题（C2C 备注 / 群改名后即时反映，等服务端下行再对齐）。
  Future<void> rename(String convId, String title) =>
      _convDao.updateTitle(convId, title);
}
