import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/providers.dart';
import '../../core/theme/lumo_colors.dart';
import '../../core/utils/time.dart';
import '../../data/models/conversation.dart';
import '../../data/models/cs_models.dart';
import '../../data/models/enums.dart';
import '../../shared/widgets/lumo_avatar.dart';

class CsWorkbenchPage extends ConsumerWidget {
  const CsWorkbenchPage({super.key, this.onOpenConversation});

  final void Function(String convId)? onOpenConversation;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authControllerProvider).user;
    if (user?.isAgent != true) {
      return const Center(
        child: Text(
          '当前账号不是客服坐席',
          style: TextStyle(color: LumoColors.textSecondary),
        ),
      );
    }
    final agentStatus = user?.agentStatus ?? 0;
    final convs = ref.watch(csConversationsProvider);
    return SafeArea(
      bottom: false,
      child: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(csConversationsProvider);
          await ref.read(csConversationsProvider.future);
        },
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 14, 16, 28),
          children: [
            Row(
              children: [
                const Expanded(
                  child: Text(
                    '客服工作台',
                    style: TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
                  ),
                ),
                IconButton(
                  tooltip: '刷新',
                  icon: const Icon(Icons.refresh_rounded),
                  onPressed: () => ref.invalidate(csConversationsProvider),
                ),
              ],
            ),
            const SizedBox(height: 10),
            _AgentStatusCard(current: agentStatus),
            const SizedBox(height: 16),
            convs.when(
              loading: () => const Padding(
                padding: EdgeInsets.only(top: 80),
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (e, _) => _ErrorPanel(
                text: '客服会话加载失败：$e',
                onRetry: () => ref.invalidate(csConversationsProvider),
              ),
              data: (items) => _CsConversationList(
                items: items,
                onOpen: (item) => _open(context, ref, item),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _open(
    BuildContext context,
    WidgetRef ref,
    CsConversation item,
  ) async {
    await _saveConversation(ref, item);
    final openConversation = onOpenConversation;
    if (openConversation != null) {
      openConversation(item.convId);
    } else if (context.mounted) {
      unawaited(context.push('/chat/${item.convId}'));
    }
  }

  Future<void> _saveConversation(WidgetRef ref, CsConversation item) {
    return ref.read(conversationRepositoryProvider).save(
          Conversation(
            convId: item.convId,
            type: ConvTypeValue.csSession,
            title: item.visitorName,
            peerUserId: item.visitorUserId,
            maxSeq: item.maxSeq,
            readSeq: '0',
            peerReadSeq: item.visitorReadSeq,
            lastMsgAbstract: item.lastMsgAbstract,
            lastMsgTime: item.lastMsgTimeMs,
            csStatus: item.csStatus.toString(),
          ),
        );
  }
}

class _AgentStatusCard extends ConsumerWidget {
  const _AgentStatusCard({required this.current});

  final int current;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              '接待状态',
              style: TextStyle(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 10),
            SegmentedButton<int>(
              segments: const [
                ButtonSegment(value: 0, label: Text('离线')),
                ButtonSegment(value: 1, label: Text('在线')),
                ButtonSegment(value: 2, label: Text('忙碌')),
              ],
              selected: {current},
              onSelectionChanged: (values) async {
                final next = values.first;
                final ok = await ref
                    .read(authControllerProvider.notifier)
                    .updateAgentStatus(next);
                if (ok) ref.invalidate(csConversationsProvider);
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text(ok ? '坐席状态已更新' : '状态更新失败')),
                  );
                }
              },
            ),
          ],
        ),
      ),
    );
  }
}

class _CsConversationList extends ConsumerWidget {
  const _CsConversationList({required this.items, required this.onOpen});

  final List<CsConversation> items;
  final void Function(CsConversation item) onOpen;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (items.isEmpty) {
      return const _EmptyCsPanel();
    }
    final sorted = [...items]..sort((a, b) {
        final aOpen = a.isOpen ? 0 : 1;
        final bOpen = b.isOpen ? 0 : 1;
        if (aOpen != bOpen) return aOpen.compareTo(bOpen);
        return b.lastMsgTimeMs.compareTo(a.lastMsgTimeMs);
      });
    return Column(
      children: [
        for (var i = 0; i < sorted.length; i++) ...[
          _CsTile(item: sorted[i], onOpen: onOpen),
          if (i != sorted.length - 1) const SizedBox(height: 10),
        ],
      ],
    );
  }
}

class _CsTile extends ConsumerStatefulWidget {
  const _CsTile({required this.item, required this.onOpen});

  final CsConversation item;
  final void Function(CsConversation item) onOpen;

  @override
  ConsumerState<_CsTile> createState() => _CsTileState();
}

class _CsTileState extends ConsumerState<_CsTile> {
  bool _busy = false;

  @override
  Widget build(BuildContext context) {
    final item = widget.item;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: BorderRadius.circular(18),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: () => widget.onOpen(item),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            children: [
              Row(
                children: [
                  LumoAvatar(name: item.visitorName, url: null, size: 46),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          item.visitorName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontWeight: FontWeight.w700),
                        ),
                        const SizedBox(height: 3),
                        Text(
                          item.lastMsgAbstract.isEmpty
                              ? '暂无消息'
                              : item.lastMsgAbstract,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: LumoColors.textSecondary,
                            fontSize: 12,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Text(
                    TimeFmt.chatTime(item.lastMsgTimeMs),
                    style: Theme.of(context).textTheme.labelSmall,
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  _CsStatusChip(status: item.csStatus),
                  if (item.visitorOnline) ...[
                    const SizedBox(width: 8),
                    const _VisitorChip(),
                  ],
                  const Spacer(),
                  if (item.isOpen)
                    TextButton.icon(
                      onPressed: _busy ? null : _claim,
                      icon: _busy
                          ? const SizedBox.square(
                              dimension: 14,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.person_add_alt_1_rounded),
                      label: const Text('认领'),
                    )
                  else if (item.isAssigned)
                    TextButton.icon(
                      onPressed: _busy ? null : _resolve,
                      icon: _busy
                          ? const SizedBox.square(
                              dimension: 14,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.check_circle_outline_rounded),
                      label: const Text('结单'),
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _claim() =>
      _run(() => ref.read(csApiProvider).claim(widget.item.convId), '已认领会话');

  Future<void> _resolve() =>
      _run(() => ref.read(csApiProvider).resolve(widget.item.convId), '会话已结单');

  Future<void> _run(Future<void> Function() action, String message) async {
    setState(() => _busy = true);
    try {
      await action();
      ref.invalidate(csConversationsProvider);
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(message)));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('操作失败：$e')));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }
}

class _CsStatusChip extends StatelessWidget {
  const _CsStatusChip({required this.status});

  final int status;

  @override
  Widget build(BuildContext context) {
    final color = switch (status) {
      1 => const Color(0xFFF59E0B),
      2 => LumoColors.primary,
      3 => LumoColors.textSecondary,
      _ => LumoColors.textSecondary,
    };
    return _Chip(
      text: switch (status) {
        1 => '待接待',
        2 => '处理中',
        3 => '已结单',
        _ => '未知',
      },
      color: color,
    );
  }
}

class _VisitorChip extends StatelessWidget {
  const _VisitorChip();

  @override
  Widget build(BuildContext context) => const _Chip(
        text: '访客在线',
        color: LumoColors.success,
      );
}

class _Chip extends StatelessWidget {
  const _Chip({required this.text, required this.color});

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
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
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

class _EmptyCsPanel extends StatelessWidget {
  const _EmptyCsPanel();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.only(top: 80),
      child: Center(
        child: Text(
          '暂无客服会话',
          style: TextStyle(color: LumoColors.textSecondary),
        ),
      ),
    );
  }
}

class _ErrorPanel extends StatelessWidget {
  const _ErrorPanel({required this.text, required this.onRetry});

  final String text;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 80),
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(text, style: const TextStyle(color: LumoColors.textSecondary)),
            const SizedBox(height: 10),
            TextButton(onPressed: onRetry, child: const Text('重试')),
          ],
        ),
      ),
    );
  }
}
