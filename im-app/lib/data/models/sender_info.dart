/// 消息发送者冗余资料（D：Sender 随消息下发，避免收到推送再查用户）。
class SenderInfo {
  const SenderInfo({
    required this.userId,
    required this.nickname,
    this.avatar,
    this.verifiedType = 0,
    this.userType = 0,
  });

  final String userId;
  final String nickname;
  final String? avatar;
  final int verifiedType; // 蓝V（VerifiedType）
  final int userType; // member/agent/visitor（客服气泡区分）

  SenderInfo copyWith({String? nickname, String? avatar}) => SenderInfo(
        userId: userId,
        nickname: nickname ?? this.nickname,
        avatar: avatar ?? this.avatar,
        verifiedType: verifiedType,
        userType: userType,
      );
}
