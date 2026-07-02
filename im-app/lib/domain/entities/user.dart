/// 用户领域实体。用于资料缓存、会话标题、官方认证标识和多端资料同步。
class User {
  const User({
    required this.userId,
    required this.tenantId,
    this.account,
    this.nickname,
    this.username,
    this.avatar,
    this.verifiedType = 0,
    this.userType = 0,
  });

  final String userId;
  final String tenantId;
  final String? account;
  final String? nickname;
  final String? username;
  final String? avatar;
  final int verifiedType;
  final int userType;
}
