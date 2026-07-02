/// 当前登录用户。account=登录凭证/手机号；username=对外可分享 handle（D42）。
class SessionUser {
  const SessionUser({
    required this.id,
    required this.tenantId,
    required this.account,
    this.nickname,
    this.username,
    this.avatar,
    this.verifiedType = 0,
    this.friendVerifyRequired = 1,
    this.isAgent = false,
    this.agentStatus = 0,
  });

  final String id;
  final String tenantId;
  final String account;
  final String? nickname;
  final String? username;
  final String? avatar;
  final int verifiedType;
  final int friendVerifyRequired; // D40：1=加我需验证，0=免验证
  final bool isAgent; // D34：坐席能力（决定是否显示「客服」Tab）
  final int agentStatus; // 0=离线 1=在线 2=忙碌

  String get displayName =>
      (nickname != null && nickname!.isNotEmpty) ? nickname! : account;

  factory SessionUser.fromJson(Map<String, dynamic> json) => SessionUser(
        id: (json['id'] ?? json['userId'] ?? '0').toString(),
        tenantId: (json['tenantId'] ?? '1').toString(),
        account: (json['account'] ?? '').toString(),
        nickname: json['nickname'] as String?,
        username: json['username'] as String?,
        avatar: json['avatar'] as String?,
        verifiedType: (json['verifiedType'] as num?)?.toInt() ?? 0,
        friendVerifyRequired:
            (json['friendVerifyRequired'] as num?)?.toInt() ?? 1,
        isAgent: json['isAgent'] == true || json['isAgent'] == 1,
        agentStatus: (json['agentStatus'] as num?)?.toInt() ?? 0,
      );
}
