import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/providers.dart';
import '../../core/theme/lumo_colors.dart';
import '../../data/models/conversation.dart';
import '../../data/models/enums.dart';
import '../../data/models/friend.dart';
import '../../shared/widgets/lumo_avatar.dart';

class CreateGroupPage extends ConsumerStatefulWidget {
  const CreateGroupPage({super.key});

  @override
  ConsumerState<CreateGroupPage> createState() => _CreateGroupPageState();
}

class _CreateGroupPageState extends ConsumerState<CreateGroupPage> {
  final _nameController = TextEditingController();
  final _searchController = TextEditingController();
  final _selected = <String>{};
  var _keyword = '';
  var _creating = false;

  @override
  void dispose() {
    _nameController.dispose();
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final friendsAsync = ref.watch(friendsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('创建群聊')),
      body: SafeArea(
        top: false,
        child: friendsAsync.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  '好友加载失败：$e',
                  style: const TextStyle(color: LumoColors.textSecondary),
                ),
                const SizedBox(height: 10),
                TextButton(
                  onPressed: () => ref.invalidate(friendsProvider),
                  child: const Text('重试'),
                ),
              ],
            ),
          ),
          data: (friends) => _buildContent(friends),
        ),
      ),
      bottomNavigationBar: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(18, 10, 18, 18),
          child: FilledButton.icon(
            onPressed: _creating ? null : _createGroup,
            icon: _creating
                ? const SizedBox.square(
                    dimension: 16,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : const Icon(Icons.group_add_rounded),
            label: Text(_creating ? '创建中' : '创建群聊 · ${_selected.length + 1} 人'),
          ),
        ),
      ),
    );
  }

  Widget _buildContent(List<Friend> friends) {
    final filtered = friends.where((f) {
      final kw = _keyword.trim().toLowerCase();
      if (kw.isEmpty) return true;
      return f.displayName.toLowerCase().contains(kw) ||
          (f.username?.toLowerCase().contains(kw) ?? false) ||
          f.userId.contains(kw);
    }).toList()
      ..sort((a, b) => a.displayName.compareTo(b.displayName));

    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 16, 18, 28),
      children: [
        TextField(
          controller: _nameController,
          maxLength: 64,
          decoration: const InputDecoration(
            labelText: '群名称',
            hintText: '例如：设计协作群',
            prefixIcon: Icon(Icons.groups_rounded),
          ),
        ),
        const SizedBox(height: 12),
        if (_selected.isNotEmpty)
          _SelectedPreview(
            friends: friends,
            selected: _selected,
            onRemove: _toggle,
          ),
        const SizedBox(height: 12),
        TextField(
          controller: _searchController,
          onChanged: (v) => setState(() => _keyword = v),
          decoration: const InputDecoration(
            hintText: '搜索好友',
            prefixIcon: Icon(Icons.search_rounded),
          ),
        ),
        const SizedBox(height: 18),
        const Text(
          '选择好友',
          style: TextStyle(
            color: LumoColors.textSecondary,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 10),
        if (friends.isEmpty)
          const _EmptyFriends()
        else if (filtered.isEmpty)
          const _EmptyFiltered()
        else
          DecoratedBox(
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.surface,
              borderRadius: BorderRadius.circular(18),
            ),
            child: Column(
              children: [
                for (var i = 0; i < filtered.length; i++) ...[
                  _FriendSelectTile(
                    friend: filtered[i],
                    selected: _selected.contains(filtered[i].userId),
                    onTap: () => _toggle(filtered[i].userId),
                  ),
                  if (i != filtered.length - 1)
                    const Divider(height: 1, indent: 72),
                ],
              ],
            ),
          ),
      ],
    );
  }

  void _toggle(String userId) {
    setState(() {
      if (_selected.contains(userId)) {
        _selected.remove(userId);
      } else {
        _selected.add(userId);
      }
    });
  }

  Future<void> _createGroup() async {
    final name = _nameController.text.trim();
    if (name.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('请输入群名称')),
      );
      return;
    }
    if (_selected.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('请至少选择一位好友')),
      );
      return;
    }
    setState(() => _creating = true);
    try {
      final group = await ref.read(groupApiProvider).createGroup(
            name: name,
            memberUserIds: _selected.toList(),
          );
      await ref.read(conversationRepositoryProvider).save(
            Conversation(
              convId: group.convId,
              type: ConvTypeValue.group,
              title: group.name,
              groupId: group.groupId,
              maxSeq: '0',
              readSeq: '0',
              lastMsgAbstract: '',
            ),
          );
      if (!mounted) return;
      context.go('/chat/${group.convId}');
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('创建群聊失败：$e')),
      );
    } finally {
      if (mounted) setState(() => _creating = false);
    }
  }
}

class _SelectedPreview extends StatelessWidget {
  const _SelectedPreview({
    required this.friends,
    required this.selected,
    required this.onRemove,
  });

  final List<Friend> friends;
  final Set<String> selected;
  final void Function(String userId) onRemove;

  @override
  Widget build(BuildContext context) {
    final items = friends.where((f) => selected.contains(f.userId)).toList();
    return DecoratedBox(
      decoration: BoxDecoration(
        color: LumoColors.primarySoft,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final friend in items)
              Chip(
                avatar: LumoAvatar(
                  name: friend.displayName,
                  url: friend.avatar,
                  size: 22,
                ),
                label: Text(friend.displayName),
                onDeleted: () => onRemove(friend.userId),
              ),
          ],
        ),
      ),
    );
  }
}

class _FriendSelectTile extends StatelessWidget {
  const _FriendSelectTile({
    required this.friend,
    required this.selected,
    required this.onTap,
  });

  final Friend friend;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      leading: LumoAvatar(
        name: friend.displayName,
        url: friend.avatar,
        size: 44,
      ),
      title: Text(friend.displayName),
      subtitle: friend.username == null || friend.username!.isEmpty
          ? null
          : Text('@${friend.username}'),
      trailing: Checkbox(value: selected, onChanged: (_) => onTap()),
      onTap: onTap,
    );
  }
}

class _EmptyFriends extends StatelessWidget {
  const _EmptyFriends();

  @override
  Widget build(BuildContext context) {
    return const _EmptyPanel(text: '还没有好友，先去添加好友');
  }
}

class _EmptyFiltered extends StatelessWidget {
  const _EmptyFiltered();

  @override
  Widget build(BuildContext context) {
    return const _EmptyPanel(text: '没有匹配的好友');
  }
}

class _EmptyPanel extends StatelessWidget {
  const _EmptyPanel({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Padding(
        padding: const EdgeInsets.all(22),
        child: Center(
          child: Text(
            text,
            style: const TextStyle(color: LumoColors.textSecondary),
          ),
        ),
      ),
    );
  }
}
