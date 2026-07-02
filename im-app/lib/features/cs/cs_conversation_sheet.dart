import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';
import '../../core/theme/lumo_colors.dart';
import '../../core/utils/time.dart';
import '../../data/models/conversation.dart';
import '../../data/models/cs_models.dart';

Future<void> showCsConversationSheet(
  BuildContext context, {
  required String convId,
}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    builder: (_) => _CsConversationSheet(convId: convId),
  );
}

class _CsConversationSheet extends ConsumerStatefulWidget {
  const _CsConversationSheet({required this.convId});

  final String convId;

  @override
  ConsumerState<_CsConversationSheet> createState() =>
      _CsConversationSheetState();
}

class _CsConversationSheetState extends ConsumerState<_CsConversationSheet> {
  final _controller = TextEditingController();
  bool _submitting = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final conv = ref.watch(conversationProvider(widget.convId)).valueOrNull;
    final notes = ref.watch(csNotesProvider(widget.convId));
    final bottom = MediaQuery.viewInsetsOf(context).bottom;
    final canUseNotes = conv?.type == 3 && conv?.csStatus != '1';
    return Padding(
      padding: EdgeInsets.only(bottom: bottom),
      child: SafeArea(
        top: false,
        child: DraggableScrollableSheet(
          expand: false,
          initialChildSize: 0.72,
          minChildSize: 0.42,
          maxChildSize: 0.92,
          builder: (context, controller) => ListView(
            controller: controller,
            padding: const EdgeInsets.fromLTRB(18, 14, 18, 24),
            children: [
              Center(
                child: Container(
                  width: 38,
                  height: 4,
                  decoration: BoxDecoration(
                    color: LumoColors.divider,
                    borderRadius: BorderRadius.circular(999),
                  ),
                ),
              ),
              const SizedBox(height: 18),
              _ConversationSummary(conv: conv),
              const SizedBox(height: 22),
              Row(
                children: [
                  const Text(
                    '内部备注',
                    style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
                  ),
                  const Spacer(),
                  Text(
                    notes.valueOrNull == null
                        ? ''
                        : '${notes.valueOrNull?.length ?? 0}/100',
                    style: const TextStyle(color: LumoColors.textSecondary),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              const Text(
                '备注只在坐席端可见，不会发送给访客。',
                style: TextStyle(color: LumoColors.textSecondary),
              ),
              const SizedBox(height: 14),
              if (!canUseNotes)
                const _InfoPanel(
                  title: '认领后可查看内部备注',
                  body: '未认领会话只展示队列摘要，不能查看完整记录或坐席协作内容。',
                )
              else ...[
                _NoteComposer(
                  controller: _controller,
                  submitting: _submitting,
                  onSubmit: _createNote,
                ),
                const SizedBox(height: 14),
                notes.when(
                  loading: () => const Padding(
                    padding: EdgeInsets.symmetric(vertical: 24),
                    child: Center(child: CircularProgressIndicator()),
                  ),
                  error: (e, _) => _InfoPanel(
                    title: '备注暂时不可用',
                    body: e.toString(),
                  ),
                  data: (items) => _NoteList(notes: items),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _createNote() async {
    final value = _controller.text.trim();
    if (value.isEmpty || _submitting) return;
    setState(() => _submitting = true);
    try {
      await ref.read(csApiProvider).createNote(widget.convId, value);
      _controller.clear();
      ref.invalidate(csNotesProvider(widget.convId));
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('内部备注已保存')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('备注保存失败：$e')),
      );
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }
}

class _ConversationSummary extends StatelessWidget {
  const _ConversationSummary({required this.conv});

  final Conversation? conv;

  @override
  Widget build(BuildContext context) {
    final c = conv;
    if (c == null) {
      return const _InfoPanel(title: '会话不存在', body: '本地暂未同步到该会话。');
    }
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              c.title,
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 10),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _StatusChip(text: _statusLabel(c.csStatus)),
                if (c.peerUserId != null)
                  _StatusChip(text: '访客ID ${c.peerUserId}'),
                _StatusChip(text: '访客已读 seq ${c.peerReadSeq ?? '0'}'),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              c.lastMsgAbstract.isEmpty ? '暂无消息' : c.lastMsgAbstract,
              style: const TextStyle(color: LumoColors.textSecondary),
            ),
            if (c.lastMsgTime != null) ...[
              const SizedBox(height: 4),
              Text(
                TimeFmt.chatTime(c.lastMsgTime),
                style: Theme.of(context).textTheme.labelSmall,
              ),
            ],
          ],
        ),
      ),
    );
  }

  String _statusLabel(String? status) => switch (status) {
        '1' => '待接待',
        '2' => '处理中',
        '3' => '已结单',
        _ => '状态未知',
      };
}

class _NoteComposer extends StatelessWidget {
  const _NoteComposer({
    required this.controller,
    required this.submitting,
    required this.onSubmit,
  });

  final TextEditingController controller;
  final bool submitting;
  final VoidCallback onSubmit;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              controller: controller,
              minLines: 2,
              maxLines: 5,
              maxLength: 2000,
              decoration: const InputDecoration(
                hintText: '记录访客诉求、处理进展或交接信息',
                counterText: '',
              ),
            ),
            const SizedBox(height: 10),
            Align(
              alignment: Alignment.centerRight,
              child: FilledButton.icon(
                onPressed: submitting ? null : onSubmit,
                icon: submitting
                    ? const SizedBox.square(
                        dimension: 16,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: Colors.white,
                        ),
                      )
                    : const Icon(Icons.add_rounded),
                label: Text(submitting ? '保存中' : '添加备注'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _NoteList extends ConsumerWidget {
  const _NoteList({required this.notes});

  final List<CsInternalNote> notes;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (notes.isEmpty) {
      return const _InfoPanel(title: '暂无内部备注', body: '坐席协作信息会显示在这里。');
    }
    final currentUserId = ref.watch(authControllerProvider).user?.id ?? '';
    return Column(
      children: [
        for (var i = 0; i < notes.length; i++) ...[
          _NoteTile(note: notes[i], currentUserId: currentUserId),
          if (i != notes.length - 1) const SizedBox(height: 10),
        ],
      ],
    );
  }
}

class _NoteTile extends StatelessWidget {
  const _NoteTile({required this.note, required this.currentUserId});

  final CsInternalNote note;
  final String currentUserId;

  @override
  Widget build(BuildContext context) {
    final author = note.agentId == currentUserId ? '我' : '坐席 ${note.agentId}';
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(author,
                    style: const TextStyle(fontWeight: FontWeight.w700)),
                const Spacer(),
                Text(
                  TimeFmt.chatTime(note.createdAtMs),
                  style: Theme.of(context).textTheme.labelSmall,
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(note.content),
          ],
        ),
      ),
    );
  }
}

class _InfoPanel extends StatelessWidget {
  const _InfoPanel({required this.title, required this.body});

  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: LumoColors.primarySoft.withValues(alpha: 0.5),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
            const SizedBox(height: 6),
            Text(body, style: const TextStyle(color: LumoColors.textSecondary)),
          ],
        ),
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: LumoColors.surfaceAlt,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
        child: Text(
          text,
          style: const TextStyle(fontSize: 12, color: LumoColors.textSecondary),
        ),
      ),
    );
  }
}
