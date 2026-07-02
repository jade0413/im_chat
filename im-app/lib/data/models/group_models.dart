/// 群聊 REST 响应模型。
class GroupInfo {
  const GroupInfo({
    required this.groupId,
    required this.convId,
    required this.name,
    required this.ownerId,
    required this.memberCount,
  });

  final String groupId;
  final String convId;
  final String name;
  final String ownerId;
  final int memberCount;

  factory GroupInfo.fromJson(Map<String, dynamic> j) => GroupInfo(
        groupId: (j['groupId'] ?? '0').toString(),
        convId: (j['convId'] ?? '0').toString(),
        name: (j['name'] ?? '群聊').toString(),
        ownerId: (j['ownerId'] ?? '0').toString(),
        memberCount: (j['memberCount'] as num?)?.toInt() ?? 0,
      );
}

class GroupMember {
  const GroupMember({
    required this.userId,
    required this.role,
    this.joinedAt,
  });

  final String userId;
  final int role; // 1=成员 2=管理员 3=群主
  final String? joinedAt;

  bool get isOwner => role == 3;
  bool get isAdmin => role == 2;

  factory GroupMember.fromJson(Map<String, dynamic> j) => GroupMember(
        userId: (j['userId'] ?? '0').toString(),
        role: (j['role'] as num?)?.toInt() ?? 1,
        joinedAt: j['joinedAt']?.toString(),
      );
}

class GroupMemberChange {
  const GroupMemberChange({
    required this.groupId,
    required this.convId,
    required this.memberCount,
    required this.changedUserIds,
  });

  final String groupId;
  final String convId;
  final int memberCount;
  final List<String> changedUserIds;

  factory GroupMemberChange.fromJson(Map<String, dynamic> j) =>
      GroupMemberChange(
        groupId: (j['groupId'] ?? '0').toString(),
        convId: (j['convId'] ?? '0').toString(),
        memberCount: (j['memberCount'] as num?)?.toInt() ?? 0,
        changedUserIds: ((j['changedUserIds'] as List?) ?? const [])
            .map((e) => e.toString())
            .toList(),
      );
}
