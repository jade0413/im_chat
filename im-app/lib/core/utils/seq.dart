import 'id.dart';

/// 会话级 seq 对齐（移植 im-web utils/seq.ts）。
///
/// 核心约定 4：离线消息 = 增量同步。客户端维护每会话「已连续同步到的 seq」(syncSeq)，
/// 上行 SYNC_REQ 带 local_max_seq，服务端回 [local+1, server_max]。
/// 不能用服务端 max_seq 直接当 syncSeq——中间可能有缺口尚未补齐。
class Seqs {
  Seqs._();

  /// 取较大的 seq。
  static String max(String? left, String right) =>
      Ids.compare(left ?? '0', right) >= 0 ? (left ?? '0') : right;

  /// 来的 seq 是否与本地 current 之间存在缺口（incoming > current+1）。
  /// 有缺口 → 触发 SYNC_REQ 拉取缺失区间。
  static bool hasGap(String currentSeq, String incomingSeq) {
    final current = BigInt.parse(currentSeq.isEmpty ? '0' : currentSeq);
    final incoming = BigInt.parse(incomingSeq.isEmpty ? '0' : incomingSeq);
    return incoming > current + BigInt.one;
  }

  /// 仅当 incoming == current+1 时把 syncSeq 前进一步；否则保持（出现缺口不前进）。
  static String advanceContiguous(String currentSeq, String incomingSeq) {
    final current = BigInt.parse(currentSeq.isEmpty ? '0' : currentSeq);
    final incoming = BigInt.parse(incomingSeq.isEmpty ? '0' : incomingSeq);
    if (incoming <= current) return currentSeq.isEmpty ? '0' : currentSeq;
    return incoming == current + BigInt.one
        ? incomingSeq
        : (currentSeq.isEmpty ? '0' : currentSeq);
  }

  /// 从一批消息的 seq 出发，从 baseSeq 连续推进出最大的「无缺口」syncSeq。
  static String contiguousFrom(Iterable<String?> seqs, {String baseSeq = '0'}) {
    var syncSeq = baseSeq.isEmpty ? '0' : baseSeq;
    final sorted = seqs
        .where((s) => s != null && s.isNotEmpty && s != '0')
        .cast<String>()
        .toList()
      ..sort(Ids.compare);
    for (final seq in sorted) {
      syncSeq = advanceContiguous(syncSeq, seq);
    }
    return syncSeq;
  }

  /// 解析本地同步水位：优先用已持久化的 syncSeq；为空则从现有消息推导。
  static String resolveLocal(String? storedSyncSeq, Iterable<String?> seqs) {
    if (storedSyncSeq != null &&
        storedSyncSeq.isNotEmpty &&
        storedSyncSeq != '0') {
      return storedSyncSeq;
    }
    return contiguousFrom(seqs, baseSeq: '0');
  }
}
