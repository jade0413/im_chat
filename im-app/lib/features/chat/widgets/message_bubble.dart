import 'dart:io';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../core/theme/lumo_colors.dart';
import '../../../core/theme/lumo_theme.dart';
import '../../../core/utils/id.dart';
import '../../../core/utils/time.dart';
import '../../../data/models/chat_message.dart';
import '../../../data/models/enums.dart';
import '../../../data/models/message_content.dart';
import '../../../data/models/system_notification.dart';
import '../../../shared/widgets/lumo_avatar.dart';

/// 单条消息气泡。按内容类型渲染；自己/对方左右分列；系统消息居中灰条。
class MessageBubble extends StatelessWidget {
  const MessageBubble({
    super.key,
    required this.message,
    required this.isSelf,
    required this.onRetry,
    this.peerReadSeq,
  });

  final ChatMessage message;
  final bool isSelf;
  final VoidCallback onRetry;
  final String? peerReadSeq;

  @override
  Widget build(BuildContext context) {
    if (message.content.kind == ContentKind.notification) {
      return _systemChip(context);
    }
    final bubble = Theme.of(context).extension<LumoBubbleTheme>() ??
        LumoBubbleTheme.of(false);
    final bg = isSelf ? bubble.selfBg : bubble.otherBg;
    final fg = isSelf ? bubble.selfText : bubble.otherText;
    final metaColor =
        isSelf ? LumoColors.bubbleSelfMeta : LumoColors.textSecondary;

    final content = Container(
      constraints: const BoxConstraints(maxWidth: 320),
      padding: _isMedia
          ? const EdgeInsets.all(4)
          : const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(LumoTheme.bubbleRadius),
      ),
      child: _bubbleContent(fg, metaColor),
    );

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment:
            isSelf ? MainAxisAlignment.end : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (!isSelf) ...[
            LumoAvatar(
                name: message.sender.nickname,
                url: message.sender.avatar,
                size: 36),
            const SizedBox(width: 8),
          ],
          Flexible(
            child: Align(
              alignment: isSelf ? Alignment.centerRight : Alignment.centerLeft,
              child: content,
            ),
          ),
          if (isSelf) const SizedBox(width: 8),
          if (isSelf)
            LumoAvatar(
                name: message.sender.nickname,
                url: message.sender.avatar,
                size: 36),
        ],
      ),
    );
  }

  bool get _isMedia =>
      message.content.kind == ContentKind.image ||
      message.content.kind == ContentKind.video;

  Widget _bubbleContent(Color fg, Color metaColor) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        _content(fg),
        const SizedBox(height: 4),
        _messageMeta(metaColor),
      ],
    );
  }

  Widget _content(Color fg) {
    final c = message.content;
    return switch (c) {
      TextBody() => _textContent(c, fg),
      ImageBody() => _imageBox(c),
      VoiceBody() => _voicePill(c, fg),
      FileBody() => _fileRow(c, fg),
      VideoBody() => _videoBox(),
      _ => Text('[不支持的消息]', style: TextStyle(color: fg)),
    };
  }

  /// 文本气泡：带 @提及时高亮 @token（群聊 @用户可见反馈）。
  Widget _textContent(TextBody c, Color fg) {
    final baseStyle = TextStyle(color: fg, fontSize: 15, height: 1.35);
    if (c.atUserIds.isEmpty || !c.text.contains('@')) {
      return Text(c.text, style: baseStyle);
    }
    final mentionColor = isSelf ? Colors.white : LumoColors.primary;
    final spans = <TextSpan>[];
    final re = RegExp(r'@[^\s@]+');
    var last = 0;
    for (final m in re.allMatches(c.text)) {
      if (m.start > last) {
        spans.add(TextSpan(text: c.text.substring(last, m.start)));
      }
      spans.add(TextSpan(
        text: c.text.substring(m.start, m.end),
        style: TextStyle(color: mentionColor, fontWeight: FontWeight.w600),
      ));
      last = m.end;
    }
    if (last < c.text.length) {
      spans.add(TextSpan(text: c.text.substring(last)));
    }
    return Text.rich(TextSpan(style: baseStyle, children: spans));
  }

  Widget _imageBox(ImageBody c) {
    final w = (c.width ?? 0) > 0 ? 200.0 : 160.0;
    final localPath = c.localPath;
    final localFile = localPath == null ? null : File(localPath);
    final preview = localFile != null && localFile.existsSync()
        ? Image.file(localFile, width: w, fit: BoxFit.cover)
        : Container(
            width: w,
            height: 140,
            color: LumoColors.surfaceAlt,
            child: const Icon(Icons.image,
                color: LumoColors.textSecondary, size: 36),
          );
    return ClipRRect(borderRadius: BorderRadius.circular(14), child: preview);
  }

  Widget _videoBox() => Stack(
        alignment: Alignment.center,
        children: [
          Container(
            width: 200,
            height: 140,
            decoration: BoxDecoration(
              color: LumoColors.surfaceAlt,
              borderRadius: BorderRadius.circular(14),
            ),
          ),
          const Icon(Icons.play_circle_fill, size: 44, color: Colors.white),
        ],
      );

  Widget _voicePill(VoiceBody c, Color fg) {
    final secs = (c.durationMs / 1000).ceil();
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(Icons.graphic_eq, color: fg, size: 18),
        const SizedBox(width: 8),
        Text("$secs''", style: TextStyle(color: fg)),
      ],
    );
  }

  Widget _fileRow(FileBody c, Color fg) {
    final size = c.size;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(Icons.insert_drive_file, color: fg),
        const SizedBox(width: 8),
        Flexible(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                c.fileName,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(color: fg),
              ),
              if (size != null)
                Text(
                  _fmtSize(size),
                  style:
                      TextStyle(color: fg.withValues(alpha: 0.7), fontSize: 11),
                ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _messageMeta(Color metaColor) {
    final time = TimeFmt.messageClock(message.sendTime);
    final children = <Widget>[
      Text(
        time,
        style: TextStyle(
          fontSize: 10,
          height: 1,
          color: metaColor,
          fontWeight: FontWeight.w400,
        ),
      ),
    ];
    if (isSelf) {
      final peerRead = peerReadSeq != null &&
          message.seq != null &&
          Ids.compare(peerReadSeq, message.seq) >= 0;
      final s = message.status;
      if (s == MessageStatus.pending) {
        children.add(
          _tail(
            Icon(
              Icons.schedule,
              size: 12,
              color: metaColor,
            ),
          ),
        ); // 待发（断网/排队）
      } else if (s == MessageStatus.sending) {
        children.add(
          _tail(
            SizedBox(
              width: 10,
              height: 10,
              child: CircularProgressIndicator(
                strokeWidth: 1.6,
                color: metaColor,
              ),
            ),
          ),
        );
      } else if (s == MessageStatus.failed) {
        children.add(
          _tail(
            GestureDetector(
              onTap: onRetry,
              child:
                  const Icon(Icons.error, size: 14, color: LumoColors.danger),
            ),
          ),
        );
      } else if (s != MessageStatus.revoked) {
        // sent / delivered / read：已读优先（read 状态或对端已读位推导）
        final read = s == MessageStatus.read || peerRead;
        final delivered = s == MessageStatus.delivered;
        children.add(
          _tail(
            Icon(
              (read || delivered) ? Icons.done_all : Icons.check,
              size: 13,
              color: metaColor,
            ),
          ),
        );
      }
    }
    return Row(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: children,
    );
  }

  Widget _tail(Widget w) =>
      Padding(padding: const EdgeInsets.only(left: 4), child: w);

  Widget _systemChip(BuildContext context) {
    final c = message.content as NotificationBody;
    final label = systemNotificationText(c);
    final canOpenFriendRequests = c.eventType == 'friend.request';
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Center(
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap:
              canOpenFriendRequests ? () => _openFriendRequests(context) : null,
          child: Container(
            padding: EdgeInsets.fromLTRB(
              10,
              4,
              10,
              canOpenFriendRequests ? 8 : 4,
            ),
            decoration: BoxDecoration(
              color: LumoColors.surfaceAlt,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  label,
                  style: const TextStyle(
                    fontSize: 12,
                    color: LumoColors.textSecondary,
                  ),
                ),
                if (canOpenFriendRequests) ...[
                  const SizedBox(height: 4),
                  const Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.person_add_alt_1_rounded,
                        size: 14,
                        color: LumoColors.primary,
                      ),
                      SizedBox(width: 4),
                      Text(
                        '查看申请',
                        style: TextStyle(
                          fontSize: 12,
                          color: LumoColors.primary,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _openFriendRequests(BuildContext context) {
    Future<void>.delayed(Duration.zero, () {
      if (context.mounted) context.push('/add-friend');
    });
  }

  String _fmtSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
  }
}
