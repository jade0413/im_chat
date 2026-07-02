abstract interface class SyncRepository {
  Future<void> syncNow();
  Future<void> recoverOutbox();
}
