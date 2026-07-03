import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../../../core/theme/lumo_colors.dart';
import '../../../core/utils/time.dart';
import '../../../data/models/chat_message.dart';
import 'message_bubble.dart';

/// 消息列表：响应式订阅本地 DB，新消息自动滚到底，滚到顶加载更早历史。
class MessageList extends ConsumerStatefulWidget {
  const MessageList({
    super.key,
    required this.convId,
    this.peerReadSeq,
    this.onQuote,
    this.selectionMode = false,
    this.selectedIds = const {},
    this.onSelectionToggle,
    this.onMergeForward,
  });
  final String convId;
  final String? peerReadSeq;
  final ValueChanged<ChatMessage>? onQuote;
  final bool selectionMode;
  final Set<String> selectedIds;
  final ValueChanged<ChatMessage>? onSelectionToggle;
  final ValueChanged<ChatMessage>? onMergeForward;

  @override
  ConsumerState<MessageList> createState() => _MessageListState();
}

class _MessageListState extends ConsumerState<MessageList> {
  final _scroll = ScrollController();
  int? _lastCount;
  String? _lastTailKey;
  bool _loadingOlder = false;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant MessageList oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.convId != widget.convId) {
      _lastCount = null;
      _lastTailKey = null;
      _loadingOlder = false;
    }
  }

  void _onScroll() {
    final pos = _scroll.position;
    if (pos.maxScrollExtent <= 0 || _loadingOlder) return;
    if (pos.pixels >= pos.maxScrollExtent - 60) {
      _loadingOlder = true;
      ref
          .read(messageRepositoryProvider)
          .loadOlder(widget.convId)
          .whenComplete(() => _loadingOlder = false);
    }
  }

  void _autoScroll(List<ChatMessage> messages) {
    final count = messages.length;
    final tailKey = messages.isEmpty ? null : messages.last.clientMsgId;
    final firstLayout = _lastCount == null;
    final tailChanged = tailKey != null && tailKey != _lastTailKey;

    _lastCount = count;
    _lastTailKey = tailKey;

    if (count == 0 || firstLayout || !tailChanged) return;

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_scroll.hasClients) return;
      _scroll.animateTo(
        _scroll.position.minScrollExtent,
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeOut,
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    final messagesAsync = ref.watch(messagesProvider(widget.convId));
    final myId = ref.watch(authControllerProvider).user?.id ?? '0';

    return messagesAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('加载失败：$e')),
      data: (messages) {
        _autoScroll(messages);
        if (messages.isEmpty) {
          return const Center(
            child: Text(
              '开始你们的对话吧',
              style: TextStyle(color: LumoColors.textSecondary),
            ),
          );
        }
        return ListView.builder(
          controller: _scroll,
          reverse: true,
          physics: const ClampingScrollPhysics(),
          padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 12),
          itemCount: messages.length,
          itemBuilder: (context, i) {
            final index = messages.length - 1 - i;
            final msg = messages[index];
            final prev = index > 0 ? messages[index - 1] : null;
            final isSelf = msg.sender.userId == myId;
            final showTime = _shouldShowTime(prev, msg);
            return Column(
              children: [
                if (showTime) _timeDivider(msg.sendTime),
                MessageBubble(
                  message: msg,
                  isSelf: isSelf,
                  selectionMode: widget.selectionMode,
                  selected: widget.selectedIds.contains(msg.clientMsgId),
                  onSelectionToggle: widget.onSelectionToggle,
                  onMergeForward: widget.onMergeForward,
                  peerReadSeq: widget.peerReadSeq,
                  onQuote: widget.onQuote,
                  onRetry: () => ref
                      .read(messageRepositoryProvider)
                      .retry(widget.convId, msg.clientMsgId),
                ),
              ],
            );
          },
        );
      },
    );
  }

  bool _shouldShowTime(ChatMessage? prev, ChatMessage cur) {
    if (prev == null) return true;
    final a = int.tryParse(prev.sendTime) ?? 0;
    final b = int.tryParse(cur.sendTime) ?? 0;
    return (b - a).abs() > 5 * 60 * 1000; // 间隔 > 5 分钟显示时间
  }

  Widget _timeDivider(String sendTime) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 10),
        child: Text(
          TimeFmt.chatTime(sendTime),
          style: const TextStyle(fontSize: 12, color: LumoColors.textSecondary),
        ),
      );
}
