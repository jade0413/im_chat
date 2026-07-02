import 'package:flutter/material.dart';

import '../../core/theme/lumo_colors.dart';
import '../../core/utils/time.dart';
import '../../data/models/conversation.dart';
import '../../shared/widgets/lumo_avatar.dart';

/// 会话列表项：头像 + 标题 + 摘要 + 时间 + 未读角标。
class ConversationTile extends StatelessWidget {
  const ConversationTile({
    super.key,
    required this.conv,
    required this.selected,
    required this.onTap,
  });

  final Conversation conv;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final unread = conv.unread;
    return InkWell(
      onTap: onTap,
      child: Container(
        color: selected ? LumoColors.primarySoft.withValues(alpha: 0.6) : null,
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        child: Row(
          children: [
            LumoAvatar(name: conv.title, url: conv.avatar, size: 46),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      if (conv.pinned)
                        const Padding(
                          padding: EdgeInsets.only(right: 4),
                          child: Icon(Icons.push_pin,
                              size: 12, color: LumoColors.textSecondary),
                        ),
                      Expanded(
                        child: Text(
                          conv.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontWeight: FontWeight.w600,
                            fontSize: 15,
                          ),
                        ),
                      ),
                      const SizedBox(width: 6),
                      Text(
                        TimeFmt.chatTime(conv.lastMsgTime),
                        style: Theme.of(context).textTheme.labelSmall,
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          conv.lastMsgAbstract,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ),
                      if (conv.muted)
                        const Icon(
                          Icons.notifications_off,
                          size: 14,
                          color: LumoColors.textSecondary,
                        ),
                      if (unread > 0 && !conv.muted) _badge(unread),
                      if (unread > 0 && conv.muted) _dot(),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _badge(int n) => Container(
        margin: const EdgeInsets.only(left: 6),
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
        constraints: const BoxConstraints(minWidth: 18),
        decoration: BoxDecoration(
          color: LumoColors.danger,
          borderRadius: BorderRadius.circular(9),
        ),
        child: Text(
          n > 99 ? '99+' : '$n',
          textAlign: TextAlign.center,
          style:
              const TextStyle(color: Colors.white, fontSize: 11, height: 1.4),
        ),
      );

  Widget _dot() => Container(
        margin: const EdgeInsets.only(left: 6),
        width: 8,
        height: 8,
        decoration: const BoxDecoration(
          color: LumoColors.danger,
          shape: BoxShape.circle,
        ),
      );
}
