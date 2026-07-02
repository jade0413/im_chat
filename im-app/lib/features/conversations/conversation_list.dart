import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/providers.dart';
import '../../core/theme/lumo_colors.dart';
import '../../data/models/conversation.dart';
import 'conversation_tile.dart';

/// 会话列表（消息 Tab / 桌面中栏复用）。onOpen 决定打开方式（移动 push / 桌面内嵌）。
class ConversationList extends ConsumerStatefulWidget {
  const ConversationList({
    super.key,
    required this.onOpen,
    this.selectedConvId,
    this.showHeader = true,
  });

  final void Function(Conversation conv) onOpen;
  final String? selectedConvId;
  final bool showHeader;

  @override
  ConsumerState<ConversationList> createState() => _ConversationListState();
}

class _ConversationListState extends ConsumerState<ConversationList> {
  int _filter = 0; // 0全部 1未读 2@我 3客服 4群聊
  static const _filters = ['全部', '未读', '@我', '客服', '群聊'];

  List<Conversation> _apply(List<Conversation> all) {
    return switch (_filter) {
      1 => all.where((c) => c.unread > 0).toList(),
      3 => all.where((c) => c.csStatus != null).toList(),
      4 => all.where((c) => c.isGroup).toList(),
      _ => all,
    };
  }

  @override
  Widget build(BuildContext context) {
    final convsAsync = ref.watch(conversationsProvider);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (widget.showHeader)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 12, 4),
            child: Row(
              children: [
                const Text(
                  '消息',
                  style: TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
                ),
                const Spacer(),
                _circleBtn(Icons.search, () => context.push('/search')),
                const SizedBox(width: 8),
                _circleBtn(
                  Icons.add,
                  () => unawaited(context.push('/create-group')),
                  filled: true,
                ),
              ],
            ),
          ),
        SizedBox(
          height: 38,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 14),
            itemCount: _filters.length,
            separatorBuilder: (_, __) => const SizedBox(width: 8),
            itemBuilder: (_, i) => ChoiceChip(
              label: Text(_filters[i]),
              selected: _filter == i,
              onSelected: (_) => setState(() => _filter = i),
              showCheckmark: false,
              selectedColor: LumoColors.primary,
              labelStyle: TextStyle(
                fontSize: 13,
                color: _filter == i ? Colors.white : LumoColors.textSecondary,
              ),
            ),
          ),
        ),
        const SizedBox(height: 4),
        Expanded(
          child: convsAsync.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (e, _) => Center(child: Text('加载失败：$e')),
            data: (all) {
              final list = _apply(all);
              if (list.isEmpty) {
                return const Center(
                  child: Text('暂无会话',
                      style: TextStyle(color: LumoColors.textSecondary)),
                );
              }
              return ListView.builder(
                itemCount: list.length,
                itemBuilder: (_, i) => ConversationTile(
                  conv: list[i],
                  selected: list[i].convId == widget.selectedConvId,
                  onTap: () => widget.onOpen(list[i]),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _circleBtn(IconData icon, VoidCallback onTap, {bool filled = false}) {
    return Material(
      color: filled ? LumoColors.primary : LumoColors.surfaceAlt,
      shape: const CircleBorder(),
      child: InkWell(
        customBorder: const CircleBorder(),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: Icon(
            icon,
            size: 20,
            color: filled ? Colors.white : LumoColors.ink,
          ),
        ),
      ),
    );
  }
}
