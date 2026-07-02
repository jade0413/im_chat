import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';
import '../../core/theme/lumo_colors.dart';
import '../../data/models/friend.dart';
import '../../shared/widgets/lumo_avatar.dart';

class AddFriendPage extends ConsumerStatefulWidget {
  const AddFriendPage({super.key});

  @override
  ConsumerState<AddFriendPage> createState() => _AddFriendPageState();
}

class _AddFriendPageState extends ConsumerState<AddFriendPage> {
  final _keywordController = TextEditingController();
  final _noteController = TextEditingController();

  var _results = const <Friend>[];
  Friend? _selected;
  bool _searching = false;
  bool _sending = false;

  @override
  void initState() {
    super.initState();
    final user = ref.read(authControllerProvider).user;
    _noteController.text = '我是${user?.displayName ?? '微光用户'}，想加你为好友';
  }

  @override
  void dispose() {
    _keywordController.dispose();
    _noteController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final incoming = ref.watch(friendRequestsProvider('incoming'));
    final outgoing = ref.watch(friendRequestsProvider('outgoing'));
    return Scaffold(
      appBar: AppBar(title: const Text('添加好友')),
      body: SafeArea(
        top: false,
        child: RefreshIndicator(
          onRefresh: _refreshRequests,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(18, 16, 18, 28),
            children: [
              _SearchField(
                controller: _keywordController,
                searching: _searching,
                onSubmitted: _search,
              ),
              const SizedBox(height: 18),
              _QuickActionsCard(onSearchContacts: _showContactsComingSoon),
              const SizedBox(height: 22),
              const _SectionTitle('搜索结果'),
              const SizedBox(height: 10),
              _buildSearchResult(),
              const SizedBox(height: 22),
              const _SectionTitle('发送添加申请'),
              const SizedBox(height: 10),
              _NoteCard(
                controller: _noteController,
                enabled: _selected != null && !_sending,
              ),
              const SizedBox(height: 22),
              const _SectionTitle('收到的申请'),
              const SizedBox(height: 10),
              _RequestList(
                role: _RequestRole.incoming,
                requests: incoming,
                onAction: _handleRequestAction,
              ),
              const SizedBox(height: 22),
              const _SectionTitle('发出的申请'),
              const SizedBox(height: 10),
              _RequestList(
                role: _RequestRole.outgoing,
                requests: outgoing,
                onAction: _handleRequestAction,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSearchResult() {
    if (_searching) {
      return const _Panel(
        child: Padding(
          padding: EdgeInsets.symmetric(vertical: 28),
          child: Center(child: CircularProgressIndicator()),
        ),
      );
    }
    if (_results.isEmpty) {
      final hasKeyword = _keywordController.text.trim().isNotEmpty;
      return _Panel(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Text(
            hasKeyword ? '未找到匹配用户' : '输入微光号或手机号后搜索',
            style: const TextStyle(color: LumoColors.textSecondary),
          ),
        ),
      );
    }
    return Column(
      children: _results
          .map(
            (user) => Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: _UserResultCard(
                user: user,
                selected: _selected?.userId == user.userId,
                sending: _sending && _selected?.userId == user.userId,
                onSelect: () => setState(() => _selected = user),
                onSend: () => _sendRequest(user),
              ),
            ),
          )
          .toList(),
    );
  }

  Future<void> _search(String raw) async {
    final keyword = raw.trim();
    if (keyword.isEmpty) {
      setState(() {
        _results = const [];
        _selected = null;
      });
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      _searching = true;
      _selected = null;
    });
    final selfId = ref.read(authControllerProvider).user?.id;
    try {
      final users = await ref.read(friendApiProvider).searchUsers(keyword);
      if (!mounted) return;
      setState(() {
        _results = users.where((u) => u.userId != selfId).toList();
      });
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('搜索失败：$e')),
      );
    } finally {
      if (mounted) setState(() => _searching = false);
    }
  }

  Future<void> _sendRequest(Friend user) async {
    setState(() {
      _selected = user;
      _sending = true;
    });
    try {
      final result = await ref.read(friendApiProvider).sendRequest(
            user.userId,
            note: _noteController.text.trim(),
          );
      ref.invalidate(friendRequestsProvider('outgoing'));
      if (result.accepted || result.alreadyFriend) {
        ref.invalidate(friendsProvider);
      }
      if (!mounted) return;
      final message = switch (result.result) {
        'accepted' => '已添加为好友',
        'already_friend' => '你们已经是好友',
        'pending' => '好友申请已发送，等待对方验证',
        _ => '好友申请已发送',
      };
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(message)));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('发送申请失败：$e')),
      );
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _handleRequestAction(
    FriendRequest request,
    _RequestAction action,
  ) async {
    final api = ref.read(friendApiProvider);
    try {
      switch (action) {
        case _RequestAction.accept:
          await api.acceptRequest(request.requestId);
          ref.invalidate(friendsProvider);
          break;
        case _RequestAction.reject:
          await api.rejectRequest(request.requestId);
          break;
        case _RequestAction.ignore:
          await api.ignoreRequest(request.requestId);
          break;
      }
      ref.invalidate(friendRequestsProvider('incoming'));
      ref.invalidate(friendRequestsProvider('outgoing'));
      if (!mounted) return;
      final message = switch (action) {
        _RequestAction.accept => '已添加为好友',
        _RequestAction.reject => '已拒绝申请',
        _RequestAction.ignore => '已忽略申请',
      };
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(message)));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('处理申请失败：$e')),
      );
    }
  }

  Future<void> _refreshRequests() async {
    ref.invalidate(friendRequestsProvider('incoming'));
    ref.invalidate(friendRequestsProvider('outgoing'));
    await Future.wait([
      ref.read(friendRequestsProvider('incoming').future),
      ref.read(friendRequestsProvider('outgoing').future),
    ]);
  }

  void _showContactsComingSoon() {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('手机联系人导入后续接入')),
    );
  }
}

class _SearchField extends StatelessWidget {
  const _SearchField({
    required this.controller,
    required this.searching,
    required this.onSubmitted,
  });

  final TextEditingController controller;
  final bool searching;
  final ValueChanged<String> onSubmitted;

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      autofocus: true,
      textInputAction: TextInputAction.search,
      onSubmitted: onSubmitted,
      decoration: InputDecoration(
        hintText: '微光号 / 手机号',
        prefixIcon: const Icon(Icons.search_rounded, color: LumoColors.primary),
        suffixIcon: searching
            ? const Padding(
                padding: EdgeInsets.all(14),
                child: SizedBox.square(
                  dimension: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              )
            : IconButton(
                tooltip: '搜索',
                icon: const Icon(Icons.arrow_forward_rounded),
                onPressed: () => onSubmitted(controller.text),
              ),
      ),
    );
  }
}

class _QuickActionsCard extends StatelessWidget {
  const _QuickActionsCard({required this.onSearchContacts});

  final VoidCallback onSearchContacts;

  @override
  Widget build(BuildContext context) {
    return _Panel(
      child: Column(
        children: [
          _QuickActionRow(
            icon: Icons.qr_code_scanner_rounded,
            color: LumoColors.primary,
            title: '扫一扫',
            subtitle: '扫描二维码名片加好友',
            onTap: () => _comingSoon(context),
          ),
          const Divider(height: 1, indent: 72),
          _QuickActionRow(
            icon: Icons.contacts_rounded,
            color: const Color(0xFF22B8A7),
            title: '手机联系人',
            subtitle: '添加通讯录里的朋友',
            badge: '3',
            onTap: onSearchContacts,
          ),
        ],
      ),
    );
  }

  void _comingSoon(BuildContext context) {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('扫码加好友后续接入')),
    );
  }
}

class _QuickActionRow extends StatelessWidget {
  const _QuickActionRow({
    required this.icon,
    required this.color,
    required this.title,
    required this.subtitle,
    required this.onTap,
    this.badge,
  });

  final IconData icon;
  final Color color;
  final String title;
  final String subtitle;
  final VoidCallback onTap;
  final String? badge;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      leading: Container(
        width: 48,
        height: 48,
        decoration: BoxDecoration(
          color: color,
          borderRadius: BorderRadius.circular(14),
        ),
        child: Icon(icon, color: Colors.white),
      ),
      title: Text(title, style: const TextStyle(fontSize: 18)),
      subtitle: Text(subtitle),
      trailing: badge == null
          ? const Icon(Icons.chevron_right, color: LumoColors.textSecondary)
          : Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: const Color(0xFFFF4D5E),
                borderRadius: BorderRadius.circular(999),
              ),
              child: Text(
                badge!,
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
      onTap: onTap,
    );
  }
}

class _UserResultCard extends StatelessWidget {
  const _UserResultCard({
    required this.user,
    required this.selected,
    required this.sending,
    required this.onSelect,
    required this.onSend,
  });

  final Friend user;
  final bool selected;
  final bool sending;
  final VoidCallback onSelect;
  final VoidCallback onSend;

  @override
  Widget build(BuildContext context) {
    return _Panel(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            InkWell(
              borderRadius: BorderRadius.circular(16),
              onTap: onSelect,
              child: Row(
                children: [
                  LumoAvatar(
                      name: user.displayName, url: user.avatar, size: 58),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Flexible(
                              child: Text(
                                user.displayName,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  fontSize: 20,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ),
                            if (user.verifiedType > 0) ...[
                              const SizedBox(width: 6),
                              const Icon(
                                Icons.verified_rounded,
                                color: Color(0xFF3B82F6),
                                size: 18,
                              ),
                            ],
                          ],
                        ),
                        const SizedBox(height: 4),
                        Text(
                          user.username == null || user.username!.isEmpty
                              ? '微光号：未设置'
                              : '微光号：${user.username}',
                          style:
                              const TextStyle(color: LumoColors.textSecondary),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: sending ? null : onSend,
              icon: sending
                  ? const SizedBox.square(
                      dimension: 16,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    )
                  : const Icon(Icons.person_add_alt_1_rounded),
              label: Text(sending ? '发送中' : '添加到通讯录'),
            ),
          ],
        ),
      ),
    );
  }
}

class _NoteCard extends StatelessWidget {
  const _NoteCard({required this.controller, required this.enabled});

  final TextEditingController controller;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return _Panel(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
        child: Column(
          children: [
            TextField(
              controller: controller,
              enabled: enabled,
              minLines: 1,
              maxLines: 3,
              maxLength: 50,
              decoration: const InputDecoration(
                hintText: '填写申请备注',
                counterText: '',
              ),
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                const Text(
                  '对方需通过验证',
                  style: TextStyle(color: LumoColors.textSecondary),
                ),
                const Spacer(),
                ValueListenableBuilder<TextEditingValue>(
                  valueListenable: controller,
                  builder: (_, value, __) => Text(
                    '${value.text.characters.length}/50',
                    style: const TextStyle(color: LumoColors.textSecondary),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

enum _RequestRole { incoming, outgoing }

enum _RequestAction { accept, reject, ignore }

class _RequestList extends StatelessWidget {
  const _RequestList({
    required this.role,
    required this.requests,
    required this.onAction,
  });

  final _RequestRole role;
  final AsyncValue<List<FriendRequest>> requests;
  final void Function(FriendRequest request, _RequestAction action) onAction;

  @override
  Widget build(BuildContext context) {
    return requests.when(
      loading: () => const _Panel(
        child: Padding(
          padding: EdgeInsets.symmetric(vertical: 20),
          child: Center(child: CircularProgressIndicator()),
        ),
      ),
      error: (e, _) => _Panel(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Text(
            '加载失败：$e',
            style: const TextStyle(color: LumoColors.textSecondary),
          ),
        ),
      ),
      data: (items) {
        if (items.isEmpty) {
          return _Panel(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Text(
                role == _RequestRole.incoming ? '暂无新的好友申请' : '暂无发出的申请',
                style: const TextStyle(color: LumoColors.textSecondary),
              ),
            ),
          );
        }
        return _Panel(
          child: Column(
            children: [
              for (var i = 0; i < items.length; i++) ...[
                _RequestTile(
                  request: items[i],
                  role: role,
                  onAction: onAction,
                ),
                if (i != items.length - 1) const Divider(height: 1, indent: 72),
              ],
            ],
          ),
        );
      },
    );
  }
}

class _RequestTile extends StatelessWidget {
  const _RequestTile({
    required this.request,
    required this.role,
    required this.onAction,
  });

  final FriendRequest request;
  final _RequestRole role;
  final void Function(FriendRequest request, _RequestAction action) onAction;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      leading: LumoAvatar(
        name: request.displayName,
        url: request.peerAvatar,
        size: 44,
      ),
      title: Text(request.displayName),
      subtitle: Text(
        request.note.isEmpty ? _statusText(request) : request.note,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: role == _RequestRole.incoming && request.pending
          ? Wrap(
              spacing: 6,
              children: [
                TextButton(
                  onPressed: () => onAction(request, _RequestAction.reject),
                  child: const Text('拒绝'),
                ),
                FilledButton(
                  onPressed: () => onAction(request, _RequestAction.accept),
                  child: const Text('同意'),
                ),
              ],
            )
          : Text(
              _statusText(request),
              style: const TextStyle(color: LumoColors.textSecondary),
            ),
    );
  }

  String _statusText(FriendRequest r) {
    if (r.pending) return '等待验证';
    if (r.accepted) return '已同意';
    if (r.rejected) return '已拒绝';
    if (r.ignored) return '已忽略';
    return '已处理';
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: const TextStyle(
        color: LumoColors.textSecondary,
        fontWeight: FontWeight.w700,
      ),
    );
  }
}

class _Panel extends StatelessWidget {
  const _Panel({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: BorderRadius.circular(18),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 16,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: child,
    );
  }
}
