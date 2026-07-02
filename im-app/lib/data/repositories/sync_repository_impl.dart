/// Sync Repository 实现预留文件。
///
/// 当前 SYNC_REQ/SYNC_RESP 由 `ImEngine` 处理；后续如果增加手动全量修复、
/// 多端游标诊断或后台同步任务，可在这里收口。
class SyncRepositoryImpl {
  const SyncRepositoryImpl();
}
