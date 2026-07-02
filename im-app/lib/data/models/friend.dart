/// 好友条目（对齐 im-server FriendItemResponse）。
class Friend {
  const Friend({
    required this.userId,
    this.remark,
    this.nickname,
    this.avatar,
    this.username,
    this.verifiedType = 0,
    this.userType = 0,
  });

  final String userId;
  final String? remark; // 本人给对方的备注名
  final String? nickname;
  final String? avatar;
  final String? username;
  final int verifiedType;
  final int userType;

  /// 展示名：备注 > 昵称 > @username；不把 userId 暴露成昵称。
  String get displayName {
    if (remark != null && remark!.isNotEmpty) return remark!;
    if (nickname != null && nickname!.isNotEmpty) return nickname!;
    if (username != null && username!.isNotEmpty) return '@$username';
    return '未设置昵称';
  }

  /// 列表分组首字母（A-Z / #）。
  String get initial {
    final n = displayName;
    if (n.isEmpty) return '#';
    final c = n[0].toUpperCase();
    return RegExp(r'[A-Z]').hasMatch(c) ? c : '#';
  }

  factory Friend.fromJson(Map<String, dynamic> j) => Friend(
        userId: (j['userId'] ?? j['id'] ?? '0').toString(),
        remark: j['remark'] as String?,
        nickname: j['nickname'] as String?,
        avatar: j['avatar'] as String?,
        username: j['username'] as String?,
        verifiedType: (j['verifiedType'] as num?)?.toInt() ?? 0,
        userType: (j['userType'] as num?)?.toInt() ?? 0,
      );
}

/// 好友申请发送结果（对齐 SendFriendRequestResponse）。
class SendFriendRequestResult {
  const SendFriendRequestResult({required this.result, this.requestId});

  final String result;
  final String? requestId;

  bool get accepted => result == 'accepted';
  bool get alreadyFriend => result == 'already_friend';
  bool get pending => result == 'pending';

  factory SendFriendRequestResult.fromJson(Map<String, dynamic>? j) =>
      SendFriendRequestResult(
        result: (j?['result'] ?? 'ok').toString(),
        requestId: j?['requestId'] == null ? null : j!['requestId'].toString(),
      );
}

/// 好友申请条目（对齐 FriendRequestItemResponse）。
class FriendRequest {
  const FriendRequest({
    required this.requestId,
    required this.fromUserId,
    required this.toUserId,
    required this.status,
    required this.peerUserId,
    this.note = '',
    this.autoAccepted = false,
    this.createTime,
    this.peerNickname,
    this.peerAvatar,
    this.peerUsername,
  });

  final String requestId;
  final String fromUserId;
  final String toUserId;
  final int status;
  final String peerUserId;
  final String note;
  final bool autoAccepted;
  final String? createTime;
  final String? peerNickname;
  final String? peerAvatar;
  final String? peerUsername;

  bool get pending => status == 0;
  bool get accepted => status == 1;
  bool get rejected => status == 2;
  bool get ignored => status == 3;

  String get displayName {
    if (peerNickname != null && peerNickname!.isNotEmpty) return peerNickname!;
    if (peerUsername != null && peerUsername!.isNotEmpty) {
      return '@$peerUsername';
    }
    return '未设置昵称';
  }

  factory FriendRequest.fromJson(Map<String, dynamic> j) => FriendRequest(
        requestId: (j['requestId'] ?? '0').toString(),
        fromUserId: (j['fromUserId'] ?? '0').toString(),
        toUserId: (j['toUserId'] ?? '0').toString(),
        note: (j['note'] ?? '').toString(),
        status: (j['status'] as num?)?.toInt() ?? 0,
        autoAccepted: j['autoAccepted'] == true || j['autoAccepted'] == 1,
        createTime: j['createTime']?.toString(),
        peerUserId: (j['peerUserId'] ?? '0').toString(),
        peerNickname: j['peerNickname'] as String?,
        peerAvatar: j['peerAvatar'] as String?,
        peerUsername: j['peerUsername'] as String?,
      );
}
