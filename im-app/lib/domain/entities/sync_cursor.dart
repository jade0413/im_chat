/// 同步游标。localSeq 表示本地已连续同步到的会话 seq，不可用 serverMaxSeq 替代。
class SyncCursor {
  const SyncCursor({
    required this.cursorKey,
    this.convId,
    this.localSeq = '0',
    this.serverMaxSeq,
    this.convListVersion,
  });

  final String cursorKey;
  final String? convId;
  final String localSeq;
  final String? serverMaxSeq;
  final String? convListVersion;
}
