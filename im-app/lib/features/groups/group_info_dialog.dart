import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';
import '../../core/theme/lumo_colors.dart';
import '../../data/models/conversation.dart';
import '../../data/models/friend.dart';
import '../../data/models/group_models.dart';
import '../../shared/widgets/lumo_avatar.dart';

class GroupInfoDialog extends ConsumerStatefulWidget {
  const GroupInfoDialog({super.key, required this.conversation});

  final Conversation conversation;

  @override
  ConsumerState<GroupInfoDialog> createState() => _GroupInfoDialogState();
}

class _GroupInfoDialogState extends ConsumerState<GroupInfoDialog> {
  var _loadingMembers = true;
  var _savingInvite = false;
  var _renaming = false;
  String? _removingUserId;
  var _members = const <GroupMember>[];
  var _memberProfiles = const <String, Friend>{};
  final _inviteSelected = <String>{};

  String get _groupId => widget.conversation.groupId ?? '';

  @override
  void initState() {
    super.initState();
    _loadMembers();
  }

  @override
  Widget build(BuildContext context) {
    final friendsAsync = ref.watch(friendsProvider);
    final self = ref.watch(authControllerProvider).user;
    final memberIds = _members.map((m) => m.userId).toSet();
    return AlertDialog(
      title: const Text('群聊信息'),
      contentPadding: const EdgeInsets.fromLTRB(20, 12, 20, 4),
      content: SizedBox(
        width: 460,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                LumoAvatar(
                  name: widget.conversation.title,
                  url: widget.conversation.avatar,
                  size: 52,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.conversation.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      Text(
                        _loadingMembers ? '成员加载中' : '${_members.length} 人',
                        style: const TextStyle(color: LumoColors.textSecondary),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  tooltip: '修改群名称',
                  onPressed: _renaming ? null : _renameGroup,
                  icon: _renaming
                      ? const SizedBox.square(
                          dimension: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.edit_outlined),
                ),
              ],
            ),
            const SizedBox(height: 18),
            const Text(
              '成员',
              style: TextStyle(
                color: LumoColors.textSecondary,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 8),
            _MemberList(
              loading: _loadingMembers,
              members: _members,
              friends: friendsAsync.valueOrNull ?? const [],
              profiles: _memberProfiles,
              selfId: self?.id,
              selfName: self?.displayName,
              selfAvatar: self?.avatar,
              removingUserId: _removingUserId,
              onRemove: _removeMember,
            ),
            const SizedBox(height: 18),
            const Text(
              '邀请好友',
              style: TextStyle(
                color: LumoColors.textSecondary,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 8),
            friendsAsync.when(
              loading: () => const Padding(
                padding: EdgeInsets.symmetric(vertical: 18),
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (e, _) => Text(
                '好友加载失败：$e',
                style: const TextStyle(color: LumoColors.textSecondary),
              ),
              data: (friends) {
                final candidates = friends
                    .where((f) => !memberIds.contains(f.userId))
                    .toList()
                  ..sort((a, b) => a.displayName.compareTo(b.displayName));
                if (candidates.isEmpty) {
                  return const Padding(
                    padding: EdgeInsets.symmetric(vertical: 14),
                    child: Text(
                      '暂无可邀请的好友',
                      style: TextStyle(color: LumoColors.textSecondary),
                    ),
                  );
                }
                return _InviteFriendList(
                  friends: candidates,
                  selected: _inviteSelected,
                  onToggle: _toggleInvite,
                );
              },
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: _savingInvite ? null : () => Navigator.of(context).pop(),
          child: const Text('关闭'),
        ),
        FilledButton.icon(
          onPressed:
              _savingInvite || _inviteSelected.isEmpty ? null : _inviteFriends,
          icon: _savingInvite
              ? const SizedBox.square(
                  dimension: 16,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: Colors.white,
                  ),
                )
              : const Icon(Icons.person_add_alt_1_rounded),
          label: Text(_savingInvite ? '邀请中' : '邀请 ${_inviteSelected.length} 人'),
        ),
      ],
    );
  }

  Future<void> _loadMembers() async {
    if (_groupId.isEmpty) {
      setState(() => _loadingMembers = false);
      return;
    }
    setState(() => _loadingMembers = true);
    try {
      final members = await ref.read(groupApiProvider).getMembers(_groupId);
      final profiles = await _loadMemberProfiles(members);
      if (!mounted) return;
      setState(() {
        _members = members;
        _memberProfiles = profiles;
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('加载群成员失败：$e')),
      );
    } finally {
      if (mounted) setState(() => _loadingMembers = false);
    }
  }

  Future<Map<String, Friend>> _loadMemberProfiles(
    List<GroupMember> members,
  ) async {
    try {
      final ids = members.map((m) => m.userId).toSet().toList();
      final profiles = await ref.read(friendApiProvider).batchUsers(ids);
      return {for (final profile in profiles) profile.userId: profile};
    } catch (_) {
      return const {};
    }
  }

  void _toggleInvite(String userId) {
    setState(() {
      if (_inviteSelected.contains(userId)) {
        _inviteSelected.remove(userId);
      } else {
        _inviteSelected.add(userId);
      }
    });
  }

  Future<void> _inviteFriends() async {
    setState(() => _savingInvite = true);
    try {
      await ref
          .read(groupApiProvider)
          .addMembers(_groupId, _inviteSelected.toList());
      _inviteSelected.clear();
      await _loadMembers();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('已邀请好友加入群聊')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('邀请失败：$e')),
      );
    } finally {
      if (mounted) setState(() => _savingInvite = false);
    }
  }

  Future<void> _renameGroup() async {
    final controller = TextEditingController(text: widget.conversation.title);
    final nextName = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('修改群名称'),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLength: 64,
          decoration: const InputDecoration(
            hintText: '输入新的群名称',
            counterText: '',
          ),
          onSubmitted: (value) => Navigator.of(ctx).pop(value),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(ctx).pop(controller.text),
            child: const Text('保存'),
          ),
        ],
      ),
    );
    controller.dispose();
    final name = nextName?.trim() ?? '';
    if (name.isEmpty || name == widget.conversation.title) return;
    setState(() => _renaming = true);
    try {
      final updated =
          await ref.read(groupApiProvider).renameGroup(_groupId, name);
      await ref
          .read(conversationRepositoryProvider)
          .rename(widget.conversation.convId, updated.name);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('群名称已更新')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('修改群名称失败：$e')),
      );
    } finally {
      if (mounted) setState(() => _renaming = false);
    }
  }

  Future<void> _removeMember(GroupMember member) async {
    final selfId = ref.read(authControllerProvider).user?.id;
    if (member.userId == selfId) return;
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('移除成员'),
        content: const Text('确定将该成员移出群聊？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('移除'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    setState(() => _removingUserId = member.userId);
    try {
      await ref.read(groupApiProvider).removeMember(_groupId, member.userId);
      await _loadMembers();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('成员已移除')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('移除成员失败：$e')),
      );
    } finally {
      if (mounted) setState(() => _removingUserId = null);
    }
  }
}

class _MemberList extends StatelessWidget {
  const _MemberList({
    required this.loading,
    required this.members,
    required this.friends,
    required this.profiles,
    this.selfId,
    this.selfName,
    this.selfAvatar,
    this.removingUserId,
    this.onRemove,
  });

  final bool loading;
  final List<GroupMember> members;
  final List<Friend> friends;
  final Map<String, Friend> profiles;
  final String? selfId;
  final String? selfName;
  final String? selfAvatar;
  final String? removingUserId;
  final void Function(GroupMember member)? onRemove;

  @override
  Widget build(BuildContext context) {
    if (loading) {
      return const SizedBox(
        height: 78,
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (members.isEmpty) {
      return const Text(
        '暂无成员',
        style: TextStyle(color: LumoColors.textSecondary),
      );
    }
    final friendById = {for (final f in friends) f.userId: f};
    return ConstrainedBox(
      constraints: const BoxConstraints(maxHeight: 170),
      child: ListView.separated(
        shrinkWrap: true,
        itemCount: members.length,
        separatorBuilder: (_, __) => const Divider(height: 1),
        itemBuilder: (_, i) {
          final member = members[i];
          final friend = friendById[member.userId] ?? profiles[member.userId];
          final isSelf = member.userId == selfId;
          final name = isSelf
              ? (selfName == null || selfName!.isEmpty ? '我' : '$selfName（我）')
              : (friend?.displayName ?? '未设置昵称');
          final avatar = isSelf ? selfAvatar : friend?.avatar;
          final role = member.isOwner
              ? const _RoleBadge(text: '群主', color: Color(0xFFF59E0B))
              : member.isAdmin
                  ? const _RoleBadge(text: '管理员', color: Color(0xFF3B82F6))
                  : null;
          return ListTile(
            dense: true,
            contentPadding: EdgeInsets.zero,
            leading: LumoAvatar(name: name, url: avatar, size: 34),
            title: Text(name),
            trailing: SizedBox(
              width: role == null ? 48 : 108,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.end,
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (role != null) Flexible(child: role),
                  if (!member.isOwner && !isSelf && onRemove != null) ...[
                    const SizedBox(width: 4),
                    IconButton(
                      tooltip: '移除成员',
                      icon: removingUserId == member.userId
                          ? const SizedBox.square(
                              dimension: 16,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.person_remove_outlined, size: 20),
                      onPressed: removingUserId == null
                          ? () => onRemove!(member)
                          : null,
                    ),
                  ],
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _InviteFriendList extends StatelessWidget {
  const _InviteFriendList({
    required this.friends,
    required this.selected,
    required this.onToggle,
  });

  final List<Friend> friends;
  final Set<String> selected;
  final void Function(String userId) onToggle;

  @override
  Widget build(BuildContext context) {
    return ConstrainedBox(
      constraints: const BoxConstraints(maxHeight: 220),
      child: ListView.separated(
        shrinkWrap: true,
        itemCount: friends.length,
        separatorBuilder: (_, __) => const Divider(height: 1),
        itemBuilder: (_, i) {
          final friend = friends[i];
          final checked = selected.contains(friend.userId);
          return ListTile(
            contentPadding: EdgeInsets.zero,
            leading: LumoAvatar(
              name: friend.displayName,
              url: friend.avatar,
              size: 38,
            ),
            title: Text(friend.displayName),
            subtitle: friend.username == null || friend.username!.isEmpty
                ? null
                : Text('@${friend.username}'),
            trailing: Checkbox(
              value: checked,
              onChanged: (_) => onToggle(friend.userId),
            ),
            onTap: () => onToggle(friend.userId),
          );
        },
      ),
    );
  }
}

class _RoleBadge extends StatelessWidget {
  const _RoleBadge({required this.text, required this.color});

  final String text;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        child: Text(
          text,
          style: TextStyle(
            color: color,
            fontSize: 12,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
    );
  }
}
