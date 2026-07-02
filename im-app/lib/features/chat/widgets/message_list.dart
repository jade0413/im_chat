import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../../../core/theme/lumo_colors.dart';
import '../../../core/utils/time.dart';
import '../../../data/models/chat_message.dart';
import 'message_bubble.dart';

/// 消息列表：响应式订阅本地 DB，新消息自动滚到底，滚到顶加载更早历史。
class MessageList extends ConsumerStatefulWidget {
  const MessageList({super.key, required this.convId, this.peerReadSeq});
  final String convId;
  final String? peerReadSeq;

  @override
  ConsumerState<MessageList> createState() => _MessageListState();
}

class _MessageListState extends ConsumerState<MessageList> {
  final _scroll = ScrollController();
  int _lastCount = 0;
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

  void _onScroll() {
    if (_scroll.position.pixels <= 60 && !_loadingOlder) {
      _loadingOlder = true;
      ref
          .read(messageRepositoryProvider)
          .loadOlder(widget.convId)
          .whenComplete(() => _loadingOlder = false);
    }
  }

  void _autoScroll(int count) {
    if (count != _lastCount) {
      _lastCount = count;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scroll.hasClients) {
          _scroll.animateTo(
            _scroll.position.maxScrollExtent,
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeOut,
          );
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final messagesAsync = ref.watch(messagesProvider(widget.convId));
    final myId = ref.watch(authControllerProvider).user?.id ?? '0';

    return messagesAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('加载失败：$e')),
      data: (messages) {
        _autoScroll(messages.length);
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
          padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 12),
          itemCount: messages.length,
          itemBuilder: (context, i) {
            final msg = messages[i];
            final prev = i > 0 ? messages[i - 1] : null;
            final isSelf = msg.sender.userId == myId;
            final showTime = _shouldShowTime(prev, msg);
            return Column(
              children: [
                if (showTime) _timeDivider(msg.sendTime),
                MessageBubble(
                  message: msg,
                  isSelf: isSelf,
                  peerReadSeq: widget.peerReadSeq,
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
