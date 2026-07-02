/// 群组领域实体。MVP 先保存群资料快照，成员、权限、公告后续扩展。
class Group {
  const Group({
    required this.groupId,
    required this.tenantId,
    required this.name,
    this.avatar,
    this.ownerUserId,
    this.memberCount = 0,
    this.maxMemberCount = 500,
  });

  final String groupId;
  final String tenantId;
  final String name;
  final String? avatar;
  final String? ownerUserId;
  final int memberCount;
  final int maxMemberCount;
}
