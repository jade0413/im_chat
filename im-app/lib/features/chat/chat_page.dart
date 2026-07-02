import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/providers.dart';
import '../../core/theme/lumo_colors.dart';
import '../../data/models/conversation.dart';
import '../../data/models/friend.dart';
import '../../shared/widgets/lumo_avatar.dart';
import '../groups/group_info_dialog.dart';
import 'widgets/input_bar.dart';
import 'widgets/message_list.dart';

/// 单个会话聊天页。embedded=true 时用于桌面右栏（无返回按钮、无 Scaffold AppBar）。
class ChatPage extends ConsumerWidget {
  const ChatPage({super.key, required this.convId, this.embedded = false});

  final String convId;
  final bool embedded;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final convAsync = ref.watch(conversationProvider(convId));
    final conv = convAsync.valueOrNull;

    // 进入/有新消息时标记已读到最新 seq。
    ref.listen(conversationProvider(convId), (_, next) {
      final c = next.valueOrNull;
      if (c != null && c.unread > 0) {
        ref.read(messageRepositoryProvider).markRead(c.convId, c.maxSeq);
      }
    });

    final body = Column(
      children: [
        Expanded(
            child: MessageList(convId: convId, peerReadSeq: conv?.peerReadSeq)),
        InputBar(convId: convId),
      ],
    );

    if (embedded) {
      return Column(
        children: [
          _Header(convId: convId, conv: conv, embedded: true),
          const Divider(height: 1),
          Expanded(child: body),
        ],
      );
    }
    return Scaffold(
      appBar: AppBar(
        titleSpacing: 0,
        leading: BackButton(onPressed: () => context.pop()),
        title: _Header(convId: convId, conv: conv, embedded: false),
      ),
      body: SafeArea(top: false, child: body),
    );
  }
}

class _Header extends ConsumerWidget {
  const _Header({required this.convId, required this.conv, required this.embedded});
  final String convId;
  final Conversation? conv;
  final bool embedded;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final title = conv?.title ?? '会话';
    final padding = embedded
        ? const EdgeInsets.symmetric(horizontal: 16, vertical: 12)
        : EdgeInsets.zero;
    final isC2C = conv?.isC2C ?? false;
    final titleBlock = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Flexible(
              child: Text(
                title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                    fontWeight: FontWeight.w600, fontSize: 16),
              ),
            ),
            // C2C：标题旁提示可点改备注
            if (isC2C) ...[
              const SizedBox(width: 6),
              const Icon(Icons.edit_outlined,
                  size: 14, color: LumoColors.textSecondary),
            ],
          ],
        ),
        if (isC2C)
          const Row(
            children: [
              Icon(Icons.circle, size: 7, color: LumoColors.success),
              SizedBox(width: 4),
              Text(
                '在线',
                style: TextStyle(
                    fontSize: 11, color: LumoColors.textSecondary),
              ),
            ],
          ),
      ],
    );

    return Padding(
      padding: padding,
      child: Row(
        children: [
          LumoAvatar(name: title, url: conv?.avatar, size: 36),
          const SizedBox(width: 10),
          Expanded(
            // 单聊：点标题即可修改备注（如图“未命名会话”可就地改名）
            child: isC2C
                ? InkWell(
                    borderRadius: BorderRadius.circular(8),
                    onTap: () => _editRemark(context, ref),
                    child: titleBlock,
                  )
                : titleBlock,
          ),
          if (conv?.isGroup ?? false)
            IconButton(
              tooltip: '群聊信息',
              onPressed: conv?.groupId == null
                  ? null
                  : () => _showGroupInfo(context, conv!),
              icon: const Icon(Icons.group_add_outlined),
            )
          else if (embedded) ...[
            IconButton(onPressed: () {}, icon: const Icon(Icons.call_outlined)),
            IconButton(onPressed: () {}, icon: const Icon(Icons.more_horiz)),
          ],
        ],
      ),
    );
  }

  void _showGroupInfo(BuildContext context, Conversation conv) {
    showDialog<void>(
      context: context,
      builder: (_) => GroupInfoDialog(conversation: conv),
    );
  }

  /// 修改好友备注：备注即会话显示名（备注 > 昵称）。空备注则回落到对方昵称。
  Future<void> _editRemark(BuildContext context, WidgetRef ref) async {
    final c = conv;
    final peerId = c?.peerUserId;
    if (c == null || peerId == null || peerId.isEmpty) return;

    // 从已加载好友列表取当前备注与昵称（用于清空备注时回落）。
    final friends = ref.read(friendsProvider).valueOrNull;
    Friend? friend;
    if (friends != null) {
      for (final f in friends) {
        if (f.userId == peerId) {
          friend = f;
          break;
        }
      }
    }
    final controller = TextEditingController(text: friend?.remark ?? '');
    final messenger = ScaffoldMessenger.of(context);

    final result = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('设置备注名'),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLength: 32,
          decoration: const InputDecoration(
            hintText: '输入备注名',
            counterText: '',
          ),
          onSubmitted: (v) => Navigator.pop(ctx, v),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, controller.text),
            child: const Text('保存'),
          ),
        ],
      ),
    );
    if (result == null) return; // 取消

    final remark = result.trim();
    try {
      await ref.read(friendApiProvider).updateRemark(peerId, remark);
      // 备注为空 → 回落昵称；否则显示备注。
      final fallback = (friend?.nickname?.isNotEmpty ?? false)
          ? friend!.nickname!
          : c.title;
      final newTitle = remark.isNotEmpty ? remark : fallback;
      await ref.read(conversationRepositoryProvider).rename(c.convId, newTitle);
      ref.invalidate(friendsProvider); // 联系人列表同步新备注
      messenger.showSnackBar(
        SnackBar(content: Text(remark.isEmpty ? '已清除备注' : '备注已更新')),
      );
    } catch (e) {
      messenger.showSnackBar(
        const SnackBar(content: Text('备注修改失败，请确认对方是你的好友')),
      );
    }
  }
}
