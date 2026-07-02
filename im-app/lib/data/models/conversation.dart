import 'enums.dart';

/// 会话（移植 im-web Conversation）。
class Conversation {
  const Conversation({
    required this.convId,
    required this.type,
    required this.title,
    required this.maxSeq,
    required this.readSeq,
    this.avatar,
    this.peerUserId,
    this.groupId,
    this.syncSeq,
    this.peerReadSeq,
    this.pinned = false,
    this.muted = false,
    this.lastMsgAbstract = '',
    this.lastMsgTime,
    this.csStatus,
    this.draft,
  });

  final String convId;
  final int type; // ConvTypeValue
  final String title; // 单聊=对方昵称 / 群=群名
  final String? avatar;
  final String? peerUserId; // C2C 有效
  final String? groupId;
  final String maxSeq;

  /// 本端已连续同步到的 seq；不能用服务端 maxSeq 替代（核心约定 4）。
  final String? syncSeq;

  /// 本端已读位置（未读角标 = maxSeq - readSeq）。
  final String readSeq;

  /// 对端已读位置（已读回执展示），READ_NOTIFY 更新。
  final String? peerReadSeq;

  final bool pinned;
  final bool muted;
  final String lastMsgAbstract;
  final String? lastMsgTime;
  final String? csStatus; // 客服预留 open/assigned/resolved
  final String? draft; // 未发送草稿（验收要求 #13）

  bool get isC2C => type == ConvTypeValue.c2c;
  bool get isGroup => type == ConvTypeValue.group;
  bool get isSystem => type == ConvTypeValue.system;

  /// 未读数（BigInt 安全相减，下限 0）。
  int get unread {
    final m = BigInt.tryParse(maxSeq) ?? BigInt.zero;
    final r = BigInt.tryParse(readSeq) ?? BigInt.zero;
    final diff = m - r;
    return diff > BigInt.zero ? diff.toInt() : 0;
  }

  Conversation copyWith({
    int? type,
    String? title,
    String? avatar,
    String? peerUserId,
    String? groupId,
    String? maxSeq,
    String? syncSeq,
    String? readSeq,
    String? peerReadSeq,
    bool? pinned,
    bool? muted,
    String? lastMsgAbstract,
    String? lastMsgTime,
    String? csStatus,
    String? draft,
  }) =>
      Conversation(
        convId: convId,
        type: type ?? this.type,
        title: title ?? this.title,
        avatar: avatar ?? this.avatar,
        peerUserId: peerUserId ?? this.peerUserId,
        groupId: groupId ?? this.groupId,
        maxSeq: maxSeq ?? this.maxSeq,
        syncSeq: syncSeq ?? this.syncSeq,
        readSeq: readSeq ?? this.readSeq,
        peerReadSeq: peerReadSeq ?? this.peerReadSeq,
        pinned: pinned ?? this.pinned,
        muted: muted ?? this.muted,
        lastMsgAbstract: lastMsgAbstract ?? this.lastMsgAbstract,
        lastMsgTime: lastMsgTime ?? this.lastMsgTime,
        csStatus: csStatus ?? this.csStatus,
        draft: draft ?? this.draft,
      );
}
