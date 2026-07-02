import '../repositories/sync_repository.dart';

class SyncMessages {
  const SyncMessages(this._sync);

  final SyncRepository _sync;

  Future<void> call() => _sync.syncNow();
}
