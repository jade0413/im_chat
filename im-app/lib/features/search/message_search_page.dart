import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/providers.dart';
import '../../core/theme/lumo_colors.dart';
import '../../core/utils/time.dart';
import '../../data/models/chat_message.dart';
import '../../data/models/message_content.dart';
import '../../data/models/message_payloads.dart';
import '../../data/models/system_notification.dart';
import '../../shared/widgets/lumo_avatar.dart';

class MessageSearchPage extends ConsumerStatefulWidget {
  const MessageSearchPage({super.key});

  @override
  ConsumerState<MessageSearchPage> createState() => _MessageSearchPageState();
}

class _MessageSearchPageState extends ConsumerState<MessageSearchPage> {
  final _controller = TextEditingController();
  Timer? _debounce;
  var _keyword = '';
  var _searching = false;
  var _results = const <ChatMessage>[];

  @override
  void dispose() {
    _debounce?.cancel();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final convs = ref.watch(conversationsProvider).valueOrNull ?? const [];
    final convById = {for (final conv in convs) conv.convId: conv};
    return Scaffold(
      appBar: AppBar(title: const Text('搜索消息')),
      body: SafeArea(
        top: false,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
              child: TextField(
                controller: _controller,
                autofocus: true,
                textInputAction: TextInputAction.search,
                onChanged: _scheduleSearch,
                onSubmitted: _search,
                decoration: InputDecoration(
                  hintText: '搜索聊天记录、文件名、系统通知',
                  prefixIcon: const Icon(Icons.search_rounded),
                  suffixIcon: _controller.text.isEmpty
                      ? null
                      : IconButton(
                          tooltip: '清空',
                          icon: const Icon(Icons.close_rounded),
                          onPressed: () {
                            _controller.clear();
                            _search('');
                          },
                        ),
                ),
              ),
            ),
            Expanded(
              child: _searching
                  ? const Center(child: CircularProgressIndicator())
                  : _SearchResultList(
                      keyword: _keyword,
                      results: _results,
                      titleOf: (convId) => convById[convId]?.title ?? '会话',
                      avatarOf: (convId) => convById[convId]?.avatar,
                      onOpen: (msg) => context.push('/chat/${msg.convId}'),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  void _scheduleSearch(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 250), () => _search(value));
  }

  Future<void> _search(String raw) async {
    final keyword = raw.trim();
    setState(() {
      _keyword = keyword;
      _searching = keyword.isNotEmpty;
      if (keyword.isEmpty) _results = const [];
    });
    if (keyword.isEmpty) return;
    try {
      final results = await ref.read(messageRepositoryProvider).search(keyword);
      if (!mounted) return;
      setState(() => _results = results);
    } finally {
      if (mounted) setState(() => _searching = false);
    }
  }
}

class _SearchResultList extends StatelessWidget {
  const _SearchResultList({
    required this.keyword,
    required this.results,
    required this.titleOf,
    required this.avatarOf,
    required this.onOpen,
  });

  final String keyword;
  final List<ChatMessage> results;
  final String Function(String convId) titleOf;
  final String? Function(String convId) avatarOf;
  final void Function(ChatMessage message) onOpen;

  @override
  Widget build(BuildContext context) {
    if (keyword.isEmpty) {
      return const _EmptyState(
        icon: Icons.search_rounded,
        text: '输入关键词搜索本地聊天记录',
      );
    }
    if (results.isEmpty) {
      return const _EmptyState(
        icon: Icons.manage_search_rounded,
        text: '没有匹配的消息',
      );
    }
    return ListView.separated(
      itemCount: results.length,
      separatorBuilder: (_, __) => const Divider(height: 1, indent: 72),
      itemBuilder: (_, i) {
        final msg = results[i];
        final title = titleOf(msg.convId);
        return ListTile(
          leading: LumoAvatar(name: title, url: avatarOf(msg.convId), size: 42),
          title: Row(
            children: [
              Expanded(
                child: Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                TimeFmt.chatTime(msg.sendTime),
                style: Theme.of(context).textTheme.labelSmall,
              ),
            ],
          ),
          subtitle: Text(
            _preview(msg.content),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          onTap: () => onOpen(msg),
        );
      },
    );
  }

  String _preview(MessageContent content) => switch (content) {
        TextBody(:final text) => text,
        NotificationBody() => systemNotificationText(content),
        ImageBody() => '[图片]',
        VoiceBody() => '[语音]',
        FileBody(:final fileName) => '[文件] $fileName',
        VideoBody(:final fileName) => '[视频] $fileName',
        CustomBody() => customPreview(content),
      };
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 54, color: LumoColors.textSecondary),
          const SizedBox(height: 12),
          Text(text, style: const TextStyle(color: LumoColors.textSecondary)),
        ],
      ),
    );
  }
}
