import '../local/daos/kv_dao.dart';

class VoiceMessageStateRepository {
  VoiceMessageStateRepository(this._kvDao);

  static const _playedPrefix = 'voice_played:';

  final KvDao _kvDao;

  Stream<bool> watchPlayed(String clientMsgId) {
    final key = _playedKey(clientMsgId);
    if (key == null) return Stream.value(false);
    return _kvDao.watch(key).map((value) => value == '1').distinct();
  }

  Future<bool> isPlayed(String clientMsgId) async {
    final key = _playedKey(clientMsgId);
    if (key == null) return false;
    return await _kvDao.get(key) == '1';
  }

  Future<void> markPlayed(String clientMsgId) async {
    final key = _playedKey(clientMsgId);
    if (key == null) return;
    await _kvDao.set(key, '1');
  }

  String? _playedKey(String clientMsgId) {
    final id = clientMsgId.trim();
    return id.isEmpty ? null : '$_playedPrefix$id';
  }
}
