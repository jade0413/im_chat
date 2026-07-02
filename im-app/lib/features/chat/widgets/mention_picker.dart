import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../../../core/theme/lumo_colors.dart';
import '../../../shared/widgets/lumo_avatar.dart';

/// @提及选择结果。userId == '-1' 表示 @所有人（对齐 TextBody.atUserIds 约定）。
class MentionResult {
  const MentionResult(this.userId, this.display);
  final String userId;
  final String display;
}

/// 弹出群成员选择器，返回被 @ 的成员（或所有人）。
Future<MentionResult?> showMentionPicker(
  BuildContext context, {
  required String groupId,
  required String selfUserId,
}) {
  return showModalBottomSheet<MentionResult>(
    context: context,
    isScrollControlled: true,
    backgroundColor: Theme.of(context).appBarTheme.backgroundColor,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (_) => _MentionSheet(groupId: groupId, selfUserId: selfUserId),
  );
}

class _MentionSheet extends ConsumerWidget {
  const _MentionSheet({required this.groupId, required this.selfUserId});
  final String groupId;
  final String selfUserId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final membersAsync = ref.watch(groupMembersProvider(groupId));
    return SafeArea(
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.of(context).size.height * 0.6,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 14),
              child: Text('选择提醒的人',
                  style: TextStyle(fontWeight: FontWeight.w600, fontSize: 15)),
            ),
            const Divider(height: 1),
            Flexible(
              child: membersAsync.when(
                data: (members) {
                  final others =
                      members.where((m) => m.userId != selfUserId).toList();
                  return ListView(
                    shrinkWrap: true,
                    children: [
                      ListTile(
                        leading: const CircleAvatar(
                          backgroundColor: LumoColors.primarySoft,
                          child:
                              Icon(Icons.groups, color: LumoColors.primary),
                        ),
                        title: const Text('所有人'),
                        onTap: () => Navigator.pop(
                            context, const MentionResult('-1', '所有人')),
                      ),
                      for (final m in others)
                        ListTile(
                          leading: LumoAvatar(
                              name: m.displayName, url: m.avatar, size: 40),
                          title: Text(m.displayName),
                          onTap: () => Navigator.pop(
                              context, MentionResult(m.userId, m.displayName)),
                        ),
                      if (others.isEmpty)
                        const Padding(
                          padding: EdgeInsets.all(24),
                          child: Center(
                            child: Text('暂无其他成员',
                                style: TextStyle(
                                    color: LumoColors.textSecondary)),
                          ),
                        ),
                    ],
                  );
                },
                loading: () => const Padding(
                  padding: EdgeInsets.all(24),
                  child: Center(child: CircularProgressIndicator()),
                ),
                error: (e, _) => Padding(
                  padding: const EdgeInsets.all(24),
                  child: Text('成员加载失败：$e',
                      style:
                          const TextStyle(color: LumoColors.textSecondary)),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
