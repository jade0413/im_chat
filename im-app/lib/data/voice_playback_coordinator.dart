import 'dart:async';

class VoicePlaybackCoordinator {
  final _activeTokenController =
      StreamController<String?>.broadcast(sync: true);
  String? _activeToken;
  var _disposed = false;

  String? get activeToken => _activeToken;

  Stream<String?> get activeTokenStream => _activeTokenController.stream;

  bool claim(String token) {
    if (token.isEmpty) {
      throw ArgumentError.value(token, 'token', 'must not be empty');
    }
    if (_activeToken == token) return false;
    _activeToken = token;
    _emit(_activeToken);
    return true;
  }

  bool release(String token) {
    if (_activeToken != token) return false;
    _activeToken = null;
    _emit(null);
    return true;
  }

  bool clear() {
    if (_activeToken == null) return false;
    _activeToken = null;
    _emit(null);
    return true;
  }

  Future<void> dispose() async {
    _disposed = true;
    await _activeTokenController.close();
  }

  void _emit(String? token) {
    if (!_disposed) _activeTokenController.add(token);
  }
}
