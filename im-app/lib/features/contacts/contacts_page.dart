import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/providers.dart';
import '../../core/theme/lumo_colors.dart';
import '../../data/models/conversation.dart';
import '../../data/models/friend.dart';
import '../../shared/widgets/lumo_avatar.dart';

/// 通讯录：真实好友列表（REST /api/v1/friend/list），点好友打开单聊。
class ContactsPage extends ConsumerWidget {
  const ContactsPage({super.key, this.onOpenConversation});

  /// 桌面端可注入：打开会话改为内嵌选中而非整页 push。
  final void Function(String convId)? onOpenConversation;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final friendsAsync = ref.watch(friendsProvider);
    final incomingRequests = ref.watch(friendRequestsProvider('incoming'));
    final pendingRequests = incomingRequests.maybeWhen(
      data: (items) => items.where((r) => r.pending).length,
      orElse: () => 0,
    );
    final groupConvs = ref.watch(conversationsProvider).maybeWhen(
          data: (all) => all.where((c) => c.isGroup).toList(),
          orElse: () => const <Conversation>[],
        );
    return SafeArea(
      bottom: false,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 12, 8),
            child: Row(
              children: [
                const Text('通讯录',
                    style:
                        TextStyle(fontSize: 22, fontWeight: FontWeight.w700)),
                const Spacer(),
                IconButton(
                  icon: const Icon(Icons.refresh),
                  onPressed: () => ref.invalidate(friendsProvider),
                ),
              ],
            ),
          ),
          _entry(
            Icons.person_add_alt_1,
            '新的朋友',
            const Color(0xFF3B82F6),
            subtitle: pendingRequests > 0 ? '$pendingRequests 条待处理申请' : null,
            badgeCount: pendingRequests,
            onTap: () => unawaited(GoRouter.of(context).push('/add-friend')),
          ),
          _entry(
            Icons.groups_2,
            '群聊',
            const Color(0xFF21C16B),
            subtitle: groupConvs.isEmpty ? '暂无群聊' : '${groupConvs.length} 个群聊',
            onTap: () => _showGroupConversations(context, ref),
          ),
          const Divider(height: 16),
          Expanded(
            child: friendsAsync.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => _ErrorView(
                onRetry: () => ref.invalidate(friendsProvider),
              ),
              data: (friends) => _FriendList(
                friends: friends,
                onTap: (f) => _openChat(context, ref, f),
                onEditRemark: (f) => _editRemark(context, ref, f),
                onRefresh: () async {
                  ref.invalidate(friendsProvider);
                  await ref.read(friendsProvider.future);
                },
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _openChat(BuildContext context, WidgetRef ref, Friend f) async {
    final messenger = ScaffoldMessenger.of(context);
    final openConversation = onOpenConversation;
    final router = openConversation == null ? GoRouter.of(context) : null;
    try {
      final convId =
          await ref.read(conversationRepositoryProvider).openC2c(f.userId);
      if (openConversation != null) {
        openConversation(convId);
      } else if (router != null) {
        unawaited(router.push('/chat/$convId'));
      }
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text('打开会话失败：$e')));
    }
  }

  void _openConversation(BuildContext context, String convId) {
    if (onOpenConversation != null) {
      onOpenConversation!(convId);
    } else {
      unawaited(GoRouter.of(context).push('/chat/$convId'));
    }
  }

  Future<void> _editRemark(
    BuildContext context,
    WidgetRef ref,
    Friend friend,
  ) async {
    final messenger = ScaffoldMessenger.of(context);
    final controller = TextEditingController(text: friend.remark ?? '');
    final remark = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('修改备注'),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLength: 64,
          decoration: InputDecoration(
            labelText: '好友备注',
            hintText: friend.nickname ?? friend.username ?? '填写好友备注',
          ),
          textInputAction: TextInputAction.done,
          onSubmitted: (_) =>
              Navigator.of(dialogContext).pop(controller.text.trim()),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () =>
                Navigator.of(dialogContext).pop(controller.text.trim()),
            child: const Text('保存'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (remark == null) return;

    try {
      await ref.read(friendApiProvider).updateRemark(friend.userId, remark);
      ref.invalidate(friendsProvider);
      messenger.showSnackBar(
        SnackBar(content: Text(remark.isEmpty ? '已清空备注' : '备注已更新')),
      );
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text('修改备注失败：$e')));
    }
  }

  Future<void> _showGroupConversations(
    BuildContext context,
    WidgetRef ref,
  ) async {
    final groups = ref.read(conversationsProvider).maybeWhen(
          data: (all) => all.where((c) => c.isGroup).toList(),
          orElse: () => const <Conversation>[],
        );
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('群聊'),
        contentPadding: const EdgeInsets.fromLTRB(0, 12, 0, 0),
        content: SizedBox(
          width: 420,
          child: groups.isEmpty
              ? const Padding(
                  padding: EdgeInsets.fromLTRB(24, 12, 24, 24),
                  child: Text(
                    '暂无群聊',
                    style: TextStyle(color: LumoColors.textSecondary),
                  ),
                )
              : _GroupConversationList(
                  conversations: groups,
                  onTap: (conv) {
                    Navigator.of(dialogContext).pop();
                    _openConversation(context, conv.convId);
                  },
                ),
        ),
        actions: [
          TextButton.icon(
            onPressed: () {
              Navigator.of(dialogContext).pop();
              unawaited(GoRouter.of(context).push('/create-group'));
            },
            icon: const Icon(Icons.group_add_rounded),
            label: const Text('创建群聊'),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('关闭'),
          ),
        ],
      ),
    );
  }

  Widget _entry(
    IconData icon,
    String label,
    Color color, {
    String? subtitle,
    int badgeCount = 0,
    VoidCallback? onTap,
  }) =>
      ListTile(
        leading: Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
              color: color, borderRadius: BorderRadius.circular(10)),
          child: Icon(icon, color: Colors.white, size: 22),
        ),
        title: Text(label, style: const TextStyle(fontWeight: FontWeight.w500)),
        subtitle: subtitle == null
            ? null
            : Text(
                subtitle,
                style: const TextStyle(
                  fontSize: 12,
                  color: LumoColors.textSecondary,
                ),
              ),
        trailing: SizedBox(
          width: badgeCount > 0 ? 58 : 24,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.end,
            mainAxisSize: MainAxisSize.min,
            children: [
              if (badgeCount > 0) ...[
                Flexible(
                  child: Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: LumoColors.danger,
                      borderRadius: BorderRadius.circular(999),
                    ),
                    child: Text(
                      badgeCount > 99 ? '99+' : '$badgeCount',
                      maxLines: 1,
                      overflow: TextOverflow.clip,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 6),
              ],
              const Icon(
                Icons.chevron_right,
                color: LumoColors.textSecondary,
              ),
            ],
          ),
        ),
        onTap: onTap,
      );
}

class _FriendList extends StatelessWidget {
  const _FriendList(
      {required this.friends,
      required this.onTap,
      required this.onEditRemark,
      required this.onRefresh});
  final List<Friend> friends;
  final void Function(Friend) onTap;
  final void Function(Friend) onEditRemark;
  final Future<void> Function() onRefresh;

  @override
  Widget build(BuildContext context) {
    if (friends.isEmpty) {
      return RefreshIndicator(
        onRefresh: onRefresh,
        child: ListView(
          children: const [
            SizedBox(height: 120),
            Center(
              child: Text('还没有好友，去「新的朋友」添加吧',
                  style: TextStyle(color: LumoColors.textSecondary)),
            ),
          ],
        ),
      );
    }
    final sorted = [...friends]..sort((a, b) => a.initial.compareTo(b.initial));
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView.builder(
        itemCount: sorted.length,
        itemBuilder: (_, i) {
          final f = sorted[i];
          final showHeader = i == 0 || sorted[i - 1].initial != f.initial;
          final subtitle = _subtitle(f);
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (showHeader)
                Container(
                  color: LumoColors.surfaceAlt,
                  padding:
                      const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                  child: Text(f.initial,
                      style: const TextStyle(
                          fontSize: 12, color: LumoColors.textSecondary)),
                ),
              ListTile(
                leading:
                    LumoAvatar(name: f.displayName, url: f.avatar, size: 42),
                title: Text(f.displayName),
                subtitle: subtitle == null
                    ? null
                    : Text(subtitle, style: const TextStyle(fontSize: 12)),
                trailing: IconButton(
                  tooltip: '修改备注',
                  icon: const Icon(Icons.edit_outlined, size: 20),
                  onPressed: () => onEditRemark(f),
                ),
                onTap: () => onTap(f),
                onLongPress: () => onEditRemark(f),
              ),
            ],
          );
        },
      ),
    );
  }

  String? _subtitle(Friend f) {
    final remark = f.remark;
    final nickname = f.nickname;
    final username = f.username;
    if (remark != null && remark.isNotEmpty) {
      if (nickname != null && nickname.isNotEmpty) return nickname;
      if (username != null && username.isNotEmpty) return '@$username';
    }
    if (username != null && username.isNotEmpty) return '@$username';
    return null;
  }
}

class _GroupConversationList extends StatelessWidget {
  const _GroupConversationList({
    required this.conversations,
    required this.onTap,
  });

  final List<Conversation> conversations;
  final void Function(Conversation) onTap;

  @override
  Widget build(BuildContext context) {
    final sorted = [...conversations]
      ..sort((a, b) => (b.lastMsgTime ?? '').compareTo(a.lastMsgTime ?? ''));
    return ConstrainedBox(
      constraints: const BoxConstraints(maxHeight: 420),
      child: ListView.separated(
        shrinkWrap: true,
        itemCount: sorted.length,
        separatorBuilder: (_, __) => const Divider(height: 1),
        itemBuilder: (_, i) {
          final conv = sorted[i];
          return ListTile(
            leading: LumoAvatar(name: conv.title, url: conv.avatar, size: 42),
            title: Text(
              conv.title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            subtitle: Text(
              conv.lastMsgAbstract.isEmpty ? '暂无消息' : conv.lastMsgAbstract,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 12),
            ),
            trailing: conv.unread > 0
                ? Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
                    decoration: BoxDecoration(
                      color: LumoColors.danger,
                      borderRadius: BorderRadius.circular(9),
                    ),
                    child: Text(
                      conv.unread > 99 ? '99+' : '${conv.unread}',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 11,
                        height: 1.4,
                      ),
                    ),
                  )
                : const Icon(
                    Icons.chevron_right,
                    color: LumoColors.textSecondary,
                  ),
            onTap: () => onTap(conv),
          );
        },
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.onRetry});
  final VoidCallback onRetry;
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text('好友加载失败',
              style: TextStyle(color: LumoColors.textSecondary)),
          const SizedBox(height: 8),
          TextButton(onPressed: onRetry, child: const Text('重试')),
        ],
      ),
    );
  }
}
