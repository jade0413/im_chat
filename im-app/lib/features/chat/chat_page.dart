import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/providers.dart';
import '../../core/theme/lumo_colors.dart';
import '../../data/call/call_engine.dart';
import '../../data/models/chat_message.dart';
import '../../data/models/conversation.dart';
import '../../data/models/friend.dart';
import '../../data/models/message_payloads.dart';
import '../../shared/widgets/lumo_avatar.dart';
import '../cs/cs_conversation_sheet.dart';
import '../groups/group_info_dialog.dart';
import 'widgets/input_bar.dart';
import 'widgets/message_list.dart';

/// 单个会话聊天页。embedded=true 时用于桌面右栏（无返回按钮、无 Scaffold AppBar）。
class ChatPage extends ConsumerStatefulWidget {
  const ChatPage({super.key, required this.convId, this.embedded = false});

  final String convId;
  final bool embedded;

  @override
  ConsumerState<ChatPage> createState() => _ChatPageState();
}

class _ChatPageState extends ConsumerState<ChatPage> {
  String? _lastMarkedConvId;
  String? _lastMarkedSeq;
  ChatMessage? _quote;
  bool _selectingMerge = false;
  final Map<String, ChatMessage> _selectedForMerge = {};

  @override
  void didUpdateWidget(covariant ChatPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.convId != widget.convId) {
      _lastMarkedConvId = null;
      _lastMarkedSeq = null;
      _quote = null;
      _selectingMerge = false;
      _selectedForMerge.clear();
    }
  }

  void _markIfUnread(Conversation? c) {
    if (c == null || c.unread <= 0) return;
    if (_lastMarkedConvId == c.convId && _lastMarkedSeq == c.maxSeq) return;
    _lastMarkedConvId = c.convId;
    _lastMarkedSeq = c.maxSeq;
    Future.microtask(() {
      if (!mounted) return;
      ref.read(messageRepositoryProvider).markRead(c.convId, c.maxSeq);
    });
  }

  @override
  Widget build(BuildContext context) {
    final convId = widget.convId;
    final convAsync = ref.watch(conversationProvider(convId));
    final conv = convAsync.valueOrNull;

    // 进入/有新消息时标记已读到最新 seq。
    ref.listen(
      conversationProvider(convId),
      (_, next) => _markIfUnread(next.valueOrNull),
    );
    _markIfUnread(conv);

    final body = Column(
      children: [
        Expanded(
          child: MessageList(
            key: ValueKey(convId),
            convId: convId,
            peerReadSeq: conv?.peerReadSeq,
            selectionMode: _selectingMerge,
            selectedIds: _selectedForMerge.keys.toSet(),
            onSelectionToggle: _toggleMergeSelection,
            onMergeForward: _startMergeSelection,
            onQuote: (message) => setState(() => _quote = message),
          ),
        ),
        if (_selectingMerge)
          _MergeForwardBar(
            count: _selectedForMerge.length,
            onCancel: _cancelMergeSelection,
            onSend: () => _sendMergeForward(context),
          )
        else
          InputBar(
            convId: convId,
            quote: _quote,
            onClearQuote: () {
              if (mounted) setState(() => _quote = null);
            },
          ),
      ],
    );

    if (widget.embedded) {
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

  void _startMergeSelection(ChatMessage message) {
    if (!isForwardableMessage(message)) return;
    setState(() {
      _quote = null;
      _selectingMerge = true;
      _selectedForMerge[message.clientMsgId] = message;
    });
  }

  void _toggleMergeSelection(ChatMessage message) {
    if (!isForwardableMessage(message)) return;
    setState(() {
      if (_selectedForMerge.containsKey(message.clientMsgId)) {
        _selectedForMerge.remove(message.clientMsgId);
      } else {
        _selectedForMerge[message.clientMsgId] = message;
      }
    });
  }

  void _cancelMergeSelection() {
    if (!_selectingMerge && _selectedForMerge.isEmpty) return;
    setState(() {
      _selectingMerge = false;
      _selectedForMerge.clear();
    });
  }

  Future<void> _sendMergeForward(BuildContext context) async {
    final selected = _selectedForMerge.values.toList();
    if (selected.isEmpty) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('请选择要合并转发的消息')));
      return;
    }
    final target = await _pickTargetConversation(context, ref);
    if (target == null || !context.mounted) return;
    final content = mergeForwardContentFromMessages(selected);
    try {
      await ref
          .read(messageRepositoryProvider)
          .sendContent(target.convId, content);
      if (!context.mounted) return;
      _cancelMergeSelection();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('已合并转发给 ${target.title}')),
      );
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('合并转发失败：$e')));
      }
    }
  }
}

class _MergeForwardBar extends StatelessWidget {
  const _MergeForwardBar({
    required this.count,
    required this.onCancel,
    required this.onSend,
  });

  final int count;
  final VoidCallback onCancel;
  final VoidCallback onSend;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      top: false,
      child: Container(
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
        decoration: const BoxDecoration(
          color: Colors.white,
          border: Border(top: BorderSide(color: LumoColors.divider)),
        ),
        child: Row(
          children: [
            TextButton.icon(
              onPressed: onCancel,
              icon: const Icon(Icons.close),
              label: const Text('取消'),
            ),
            Expanded(
              child: Center(
                child: Text(
                  '已选 $count 条',
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
              ),
            ),
            FilledButton.icon(
              onPressed: count > 0 ? onSend : null,
              icon: const Icon(Icons.library_books_outlined),
              label: const Text('合并转发'),
            ),
          ],
        ),
      ),
    );
  }
}

Future<Conversation?> _pickTargetConversation(
  BuildContext context,
  WidgetRef ref,
) async {
  final convs = ref.read(conversationsProvider).valueOrNull ?? const [];
  final targets = convs.where((conv) => !conv.isSystem).toList();
  if (targets.isEmpty) {
    ScaffoldMessenger.of(context)
        .showSnackBar(const SnackBar(content: Text('没有可转发的会话')));
    return null;
  }
  return showModalBottomSheet<Conversation>(
    context: context,
    showDragHandle: true,
    builder: (sheetContext) => SafeArea(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxHeight: 420),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text(
                  '选择转发会话',
                  style: TextStyle(fontWeight: FontWeight.w700),
                ),
              ),
            ),
            Flexible(
              child: ListView.builder(
                shrinkWrap: true,
                itemCount: targets.length,
                itemBuilder: (_, index) {
                  final conv = targets[index];
                  return ListTile(
                    leading: LumoAvatar(
                        name: conv.title, url: conv.avatar, size: 36),
                    title: Text(
                      conv.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    subtitle: Text(
                      conv.isGroup
                          ? '群聊'
                          : conv.isCs
                              ? '客服会话'
                              : '单聊',
                    ),
                    onTap: () => Navigator.pop(sheetContext, conv),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _Header extends ConsumerWidget {
  const _Header(
      {required this.convId, required this.conv, required this.embedded});
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
    final groupConv =
        conv?.isGroup == true && conv?.groupId != null ? conv : null;
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
                style:
                    const TextStyle(fontWeight: FontWeight.w600, fontSize: 16),
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
                style: TextStyle(fontSize: 11, color: LumoColors.textSecondary),
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
          // D45：单聊语音通话入口（对方 userId 已知的 C2C 会话）
          if (isC2C && conv?.peerUserId != null)
            IconButton(
              tooltip: '语音通话',
              icon: const Icon(Icons.call_outlined),
              onPressed: () async {
                final ok = await ref.read(callEngineProvider).startCall(
                      conv!.peerUserId!,
                      peerName: title,
                    );
                if (!ok && context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('当前无法发起通话（连接未就绪或已在通话中）')),
                  );
                }
              },
            ),
          if (isC2C && conv?.peerUserId != null)
            IconButton(
              tooltip: '视频通话',
              icon: const Icon(Icons.videocam_outlined),
              onPressed: () async {
                final ok = await ref.read(callEngineProvider).startCall(
                      conv!.peerUserId!,
                      peerName: title,
                      media: CallMedia.video,
                    );
                if (!ok && context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('当前无法发起通话（连接未就绪或已在通话中）')),
                  );
                }
              },
            ),
          if (groupConv != null) ...[
            IconButton(
              tooltip: '群语音',
              icon: const Icon(Icons.groups_2_outlined),
              onPressed: () async {
                final ok = await ref.read(callEngineProvider).startGroupCall(
                      groupConv.groupId!,
                      groupName: title,
                    );
                if (!ok && context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('当前无法发起通话（连接未就绪或已在通话中）')),
                  );
                }
              },
            ),
            IconButton(
              tooltip: '群视频',
              icon: const Icon(Icons.video_call_outlined),
              onPressed: () async {
                final ok = await ref.read(callEngineProvider).startGroupCall(
                      groupConv.groupId!,
                      groupName: title,
                      media: CallMedia.video,
                    );
                if (!ok && context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('当前无法发起通话（连接未就绪或已在通话中）')),
                  );
                }
              },
            ),
            IconButton(
              tooltip: '群聊信息',
              onPressed: () => _showGroupInfo(context, groupConv),
              icon: const Icon(Icons.group_add_outlined),
            ),
          ] else if (conv?.isCs ?? false) ...[
            if (conv?.csStatus == '1')
              IconButton(
                tooltip: '认领会话',
                onPressed: () => _claimCs(context, ref),
                icon: const Icon(Icons.person_add_alt_1_outlined),
              ),
            if (conv?.csStatus == '2')
              IconButton(
                tooltip: '结单',
                onPressed: () => _resolveCs(context, ref),
                icon: const Icon(Icons.check_circle_outline_rounded),
              ),
            IconButton(
              tooltip: '会话资料',
              onPressed: () => showCsConversationSheet(context, convId: convId),
              icon: const Icon(Icons.article_outlined),
            ),
          ] else if (embedded)
            IconButton(onPressed: () {}, icon: const Icon(Icons.call_outlined)),
          PopupMenuButton<String>(
            tooltip: '更多',
            icon: const Icon(Icons.more_horiz),
            onSelected: (value) {
              if (value == 'clear') _clearLocalMessages(context, ref);
            },
            itemBuilder: (_) => const [
              PopupMenuItem(
                value: 'clear',
                child: Row(
                  children: [
                    Icon(Icons.cleaning_services_outlined, size: 18),
                    SizedBox(width: 8),
                    Text('清空本地聊天记录'),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Future<void> _clearLocalMessages(BuildContext context, WidgetRef ref) async {
    final c = conv;
    if (c == null) return;
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('清空本地聊天记录'),
        content: const Text('只会删除本机缓存，不会删除服务端漫游记录。重新同步或拉历史时仍可能恢复。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('清空'),
          ),
        ],
      ),
    );
    if (confirm != true || !context.mounted) return;
    final cleanup = await ref.read(messageRepositoryProvider).clearLocal(
          c.convId,
        );
    await ref.read(conversationRepositoryProvider).clearPreview(c.convId);
    if (context.mounted) {
      final cleanedMedia =
          cleanup.deletedFiles > 0 || cleanup.clearedDownloadUrlEntries > 0;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            cleanedMedia ? '已清空本地聊天记录，并清理媒体缓存' : '已清空本地聊天记录',
          ),
        ),
      );
    }
  }

  Future<void> _claimCs(BuildContext context, WidgetRef ref) async {
    final c = conv;
    if (c == null) return;
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(csApiProvider).claim(c.convId);
      await ref
          .read(conversationRepositoryProvider)
          .save(c.copyWith(csStatus: '2'));
      ref.invalidate(csConversationsProvider);
      messenger.showSnackBar(const SnackBar(content: Text('已认领会话')));
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text('认领失败：$e')));
    }
  }

  Future<void> _resolveCs(BuildContext context, WidgetRef ref) async {
    final c = conv;
    if (c == null) return;
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(csApiProvider).resolve(c.convId);
      await ref
          .read(conversationRepositoryProvider)
          .save(c.copyWith(csStatus: '3'));
      ref.invalidate(csConversationsProvider);
      messenger.showSnackBar(const SnackBar(content: Text('会话已结单')));
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text('结单失败：$e')));
    }
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
      final friendNickname = friend?.nickname;
      final fallback = friendNickname != null && friendNickname.isNotEmpty
          ? friendNickname
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
