import 'dart:async';
import 'dart:io';

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:video_player/video_player.dart';

import '../../../app/providers.dart';
import '../../../core/theme/lumo_colors.dart';
import '../../../core/theme/lumo_theme.dart';
import '../../../core/utils/id.dart';
import '../../../core/utils/time.dart';
import '../../../data/models/chat_message.dart';
import '../../../data/models/conversation.dart';
import '../../../data/models/enums.dart';
import '../../../data/models/message_content.dart';
import '../../../data/models/message_payloads.dart';
import '../../../data/models/system_notification.dart';
import '../../../data/voice_playback_coordinator.dart';
import '../../../shared/widgets/lumo_avatar.dart';

/// 单条消息气泡。按内容类型渲染；自己/对方左右分列；系统消息居中灰条。
class MessageBubble extends ConsumerStatefulWidget {
  const MessageBubble({
    super.key,
    required this.message,
    required this.isSelf,
    required this.onRetry,
    this.selectionMode = false,
    this.selected = false,
    this.onSelectionToggle,
    this.onMergeForward,
    this.onQuote,
    this.peerReadSeq,
  });

  final ChatMessage message;
  final bool isSelf;
  final VoidCallback onRetry;
  final bool selectionMode;
  final bool selected;
  final ValueChanged<ChatMessage>? onSelectionToggle;
  final ValueChanged<ChatMessage>? onMergeForward;
  final ValueChanged<ChatMessage>? onQuote;
  final String? peerReadSeq;

  @override
  ConsumerState<MessageBubble> createState() => _MessageBubbleState();
}

class _MessageBubbleState extends ConsumerState<MessageBubble> {
  AudioPlayer? _voicePlayer;
  StreamSubscription<PlayerState>? _voiceStateSub;
  StreamSubscription<Duration>? _voicePositionSub;
  StreamSubscription<Duration>? _voiceDurationSub;
  StreamSubscription<void>? _voiceCompleteSub;
  StreamSubscription<String?>? _activeVoiceSub;
  late final VoicePlaybackCoordinator _voiceCoordinator;
  bool _voiceLoading = false;
  bool _voicePlaying = false;
  Duration _voicePosition = Duration.zero;
  Duration? _voiceDuration;
  String? _openingObjectKey;
  double? _openingProgress;

  ChatMessage get message => widget.message;
  bool get isSelf => widget.isSelf;
  VoidCallback get onRetry => widget.onRetry;
  String? get peerReadSeq => widget.peerReadSeq;
  String get _voiceToken => message.clientMsgId;

  @override
  void initState() {
    super.initState();
    _voiceCoordinator = ref.read(voicePlaybackCoordinatorProvider);
    _activeVoiceSub =
        _voiceCoordinator.activeTokenStream.listen(_onActiveVoiceChanged);
  }

  @override
  void didUpdateWidget(covariant MessageBubble oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.message.clientMsgId != widget.message.clientMsgId) {
      _disposeVoicePlayer(token: oldWidget.message.clientMsgId);
      _clearVoiceState();
    }
  }

  @override
  void dispose() {
    unawaited(_activeVoiceSub?.cancel());
    _disposeVoicePlayer();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (message.isRevoked) {
      final label = isSelf ? '你撤回了一条消息' : '${message.sender.nickname}撤回了一条消息';
      return _centerChip(label);
    }
    if (message.content.kind == ContentKind.notification) {
      return _systemChip(context);
    }
    final bubble = Theme.of(context).extension<LumoBubbleTheme>() ??
        LumoBubbleTheme.of(false);
    final bg = isSelf ? bubble.selfBg : bubble.otherBg;
    final fg = isSelf ? bubble.selfText : bubble.otherText;
    final metaColor =
        isSelf ? LumoColors.bubbleSelfMeta : LumoColors.textSecondary;

    final canSelect = isForwardableMessage(message);
    final content = GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: widget.selectionMode && canSelect
          ? () => widget.onSelectionToggle?.call(message)
          : null,
      onLongPress: widget.selectionMode ? null : () => _showActions(context),
      onSecondaryTap: widget.selectionMode ? null : () => _showActions(context),
      child: Container(
        constraints: const BoxConstraints(maxWidth: 320),
        padding: _isMedia
            ? const EdgeInsets.all(4)
            : const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.circular(LumoTheme.bubbleRadius),
        ),
        child: _bubbleContent(context, ref, fg, metaColor),
      ),
    );

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment:
            isSelf ? MainAxisAlignment.end : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (widget.selectionMode)
            Padding(
              padding: const EdgeInsets.only(right: 6),
              child: Checkbox(
                value: widget.selected,
                onChanged: canSelect
                    ? (_) => widget.onSelectionToggle?.call(message)
                    : null,
                visualDensity: VisualDensity.compact,
                materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
              ),
            ),
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

  Widget _bubbleContent(
    BuildContext context,
    WidgetRef ref,
    Color fg,
    Color metaColor,
  ) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        _content(context, ref, fg),
        const SizedBox(height: 4),
        _messageMeta(metaColor),
      ],
    );
  }

  Widget _content(BuildContext context, WidgetRef ref, Color fg) {
    final c = message.content;
    return switch (c) {
      TextBody() => _textContent(c, fg),
      ImageBody() => _imageBox(context, ref, c),
      VoiceBody() => _voicePill(context, ref, c, fg),
      FileBody() => _fileRow(context, ref, c, fg),
      VideoBody() => _videoBox(context, ref, c, fg),
      CustomBody() => _customBox(context, c, fg),
      _ => Text('[不支持的消息]', style: TextStyle(color: fg)),
    };
  }

  Widget _customBox(BuildContext context, CustomBody c, Color fg) {
    final quote = parseQuoteReply(c);
    if (quote != null) {
      return ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 260),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              padding: const EdgeInsets.fromLTRB(8, 6, 8, 6),
              decoration: BoxDecoration(
                color: fg.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(8),
                border: Border(left: BorderSide(color: fg, width: 3)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    quote.senderName.isEmpty ? '原消息' : quote.senderName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: fg,
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    quote.preview,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(color: fg.withValues(alpha: 0.74)),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            Text(quote.text, style: TextStyle(color: fg, fontSize: 15)),
          ],
        ),
      );
    }
    final merge = parseMergeForward(c);
    if (merge != null) {
      return GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: widget.selectionMode
            ? null
            : () => _showMergeForwardDetail(context, merge),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 260),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.library_books_outlined, color: fg, size: 18),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      merge.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(color: fg, fontWeight: FontWeight.w700),
                    ),
                  ),
                  Icon(Icons.chevron_right,
                      color: fg.withValues(alpha: 0.72), size: 18),
                ],
              ),
              const SizedBox(height: 8),
              ...merge.items.take(3).map(
                    (item) => Padding(
                      padding: const EdgeInsets.only(top: 3),
                      child: Text(
                        '${item.senderName}: ${item.preview}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          color: fg.withValues(alpha: 0.76),
                          fontSize: 12,
                        ),
                      ),
                    ),
                  ),
            ],
          ),
        ),
      );
    }
    return Text('[${c.customType}]', style: TextStyle(color: fg));
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

  Widget _imageBox(BuildContext context, WidgetRef ref, ImageBody c) {
    final size = _imagePreviewSize(c);
    final localFile = isSelf ? _localFile(c.localPath) : null;
    final preview = _imagePreview(ref, c, localFile, size);
    return GestureDetector(
      onTap: () => _openImagePreview(context, c, localFile),
      child: ClipRRect(borderRadius: BorderRadius.circular(14), child: preview),
    );
  }

  Widget _imagePreview(
    WidgetRef ref,
    ImageBody c,
    File? localFile,
    Size size,
  ) {
    final objectKey =
        (c.thumbKey?.isNotEmpty ?? false) ? c.thumbKey! : c.objectKey;
    if (localFile != null) {
      return Container(
        width: size.width,
        height: size.height,
        color: LumoColors.surfaceAlt,
        child: Image.file(
          localFile,
          fit: BoxFit.contain,
          errorBuilder: (_, __, ___) => _remoteImagePreview(
            ref,
            objectKey,
            size,
          ),
        ),
      );
    }
    return _remoteImagePreview(ref, objectKey, size);
  }

  Widget _remoteImagePreview(WidgetRef ref, String objectKey, Size size) {
    if (objectKey.isEmpty) return _imagePlaceholder(size);
    final file = ref.watch(fileCacheProvider(objectKey));
    return file.when(
      data: (value) => Container(
        width: size.width,
        height: size.height,
        color: LumoColors.surfaceAlt,
        child: Image.file(
          value,
          fit: BoxFit.contain,
          errorBuilder: (_, __, ___) => _imagePlaceholder(size),
        ),
      ),
      loading: () => _imagePlaceholder(size, loading: true),
      error: (_, __) => _imagePlaceholder(size),
    );
  }

  Size _imagePreviewSize(ImageBody c) {
    final rawWidth = (c.width ?? 0).toDouble();
    final rawHeight = (c.height ?? 0).toDouble();
    var ratio = rawWidth > 0 && rawHeight > 0 ? rawWidth / rawHeight : 4 / 3;
    ratio = ratio.clamp(0.35, 3.2).toDouble();

    const maxWidth = 280.0;
    const maxHeight = 360.0;
    const minWidth = 112.0;
    const minHeight = 96.0;

    var width = maxWidth;
    var height = width / ratio;
    if (height > maxHeight) {
      height = maxHeight;
      width = height * ratio;
    }
    if (width < minWidth) {
      width = minWidth;
      height = width / ratio;
    }
    if (height < minHeight) {
      height = minHeight;
      width = height * ratio;
    }
    return Size(width, height);
  }

  Widget _imagePlaceholder(Size size, {bool loading = false}) => Container(
        width: size.width,
        height: size.height,
        color: LumoColors.surfaceAlt,
        alignment: Alignment.center,
        child: loading
            ? const SizedBox.square(
                dimension: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : const Icon(
                Icons.image,
                color: LumoColors.textSecondary,
                size: 36,
              ),
      );

  void _openImagePreview(
    BuildContext context,
    ImageBody c,
    File? localFile,
  ) {
    showDialog<void>(
      context: context,
      barrierColor: Colors.black87,
      builder: (dialogContext) {
        return Consumer(
          builder: (_, dialogRef, __) => Dialog.fullscreen(
            backgroundColor: Colors.black,
            child: Stack(
              children: [
                Positioned.fill(
                  child: Center(
                    child: InteractiveViewer(
                      minScale: 0.8,
                      maxScale: 4,
                      child: _fullImage(dialogRef, c, localFile),
                    ),
                  ),
                ),
                Positioned(
                  top: 18,
                  right: 18,
                  child: IconButton.filled(
                    tooltip: '关闭',
                    onPressed: () => Navigator.of(dialogContext).pop(),
                    icon: const Icon(Icons.close),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _fullImage(WidgetRef ref, ImageBody c, File? localFile) {
    if (localFile != null) {
      return Image.file(
        localFile,
        fit: BoxFit.contain,
        errorBuilder: (_, __, ___) => _remoteFullImage(ref, c.objectKey),
      );
    }
    return _remoteFullImage(ref, c.objectKey);
  }

  Widget _remoteFullImage(WidgetRef ref, String objectKey) {
    if (objectKey.isEmpty) {
      return const Icon(Icons.broken_image, color: Colors.white54, size: 48);
    }
    final file = ref.watch(fileCacheProvider(objectKey));
    return file.when(
      data: (value) => Image.file(
        value,
        fit: BoxFit.contain,
        errorBuilder: (_, __, ___) =>
            const Icon(Icons.broken_image, color: Colors.white54, size: 48),
      ),
      loading: () => const CircularProgressIndicator(),
      error: (_, __) =>
          const Icon(Icons.broken_image, color: Colors.white54, size: 48),
    );
  }

  File? _localFile(String? path) {
    if (path == null || path.isEmpty) return null;
    return File(path);
  }

  Widget _videoBox(
    BuildContext context,
    WidgetRef ref,
    VideoBody c,
    Color fg,
  ) {
    final opening = _openingObjectKey == c.objectKey;
    return InkWell(
      borderRadius: BorderRadius.circular(14),
      onTap: opening ? null : () => _openVideoPreview(context, ref, c),
      child: Stack(
        alignment: Alignment.center,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(14),
            child: SizedBox(
              width: 240,
              height: 140,
              child: _videoCover(ref, c),
            ),
          ),
          Positioned.fill(
            child: DecoratedBox(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(14),
                gradient: const LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [Colors.transparent, Colors.black54],
                ),
              ),
            ),
          ),
          opening
              ? const SizedBox.square(
                  dimension: 28,
                  child: CircularProgressIndicator(
                    strokeWidth: 2.4,
                    color: Colors.white,
                  ),
                )
              : const Icon(
                  Icons.play_circle_fill,
                  size: 48,
                  color: Colors.white,
                ),
          Positioned(
            left: 10,
            right: 10,
            bottom: 9,
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    c.fileName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                if ((c.durationMs ?? 0) > 0) ...[
                  const SizedBox(width: 8),
                  Text(
                    _fmtDuration(c.durationMs!),
                    style: const TextStyle(color: Colors.white, fontSize: 12),
                  ),
                ],
              ],
            ),
          ),
          if (opening && _openingProgress != null)
            Positioned(
              left: 10,
              right: 10,
              top: 10,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(99),
                child: LinearProgressIndicator(
                  minHeight: 3,
                  value: _openingProgress,
                  color: Colors.white,
                  backgroundColor: Colors.white24,
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _videoCover(WidgetRef ref, VideoBody c) {
    final thumbKey = c.thumbKey;
    if (thumbKey == null || thumbKey.isEmpty) {
      return _videoCoverFallback();
    }
    final file = ref.watch(fileCacheProvider(thumbKey));
    return file.when(
      data: (value) => Image.file(
        value,
        fit: BoxFit.cover,
        errorBuilder: (_, __, ___) => _videoCoverFallback(),
      ),
      loading: () => _videoCoverFallback(loading: true),
      error: (_, __) => _videoCoverFallback(),
    );
  }

  Widget _videoCoverFallback({bool loading = false}) => Container(
        color: Colors.black87,
        alignment: Alignment.center,
        child: loading
            ? const SizedBox.square(
                dimension: 18,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: Colors.white70,
                ),
              )
            : const Icon(Icons.movie_outlined, size: 42, color: Colors.white38),
      );

  Widget _voicePill(
      BuildContext context, WidgetRef ref, VoiceBody c, Color fg) {
    final played = isSelf ||
        (ref.watch(voiceMessagePlayedProvider(_voiceToken)).valueOrNull ??
            false);
    final totalMs = _voiceDuration?.inMilliseconds ?? c.durationMs;
    final totalSecs = ((totalMs <= 0 ? 1000 : totalMs) / 1000).ceil();
    final playedSecs = _voicePosition.inSeconds.clamp(0, totalSecs);
    final progress = totalMs > 0
        ? (_voicePosition.inMilliseconds / totalMs).clamp(0.0, 1.0).toDouble()
        : 0.0;
    final active =
        _voiceLoading || _voicePlaying || _voicePosition > Duration.zero;
    final width = (112 + totalSecs * 4).clamp(120, 210).toDouble();
    return InkWell(
      borderRadius: BorderRadius.circular(18),
      onTap: () => _toggleVoice(context, ref, c),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 3),
        child: SizedBox(
          width: width,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  AnimatedSwitcher(
                    duration: const Duration(milliseconds: 160),
                    child: _voiceLoading
                        ? SizedBox.square(
                            key: const ValueKey('loading'),
                            dimension: 18,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: fg,
                            ),
                          )
                        : Icon(
                            _voicePlaying
                                ? Icons.pause_rounded
                                : Icons.play_arrow_rounded,
                            key: ValueKey(_voicePlaying),
                            color: fg,
                            size: 22,
                          ),
                  ),
                  if (!played) ...[
                    const SizedBox(width: 5),
                    const _UnreadVoiceDot(),
                  ],
                  const SizedBox(width: 8),
                  Expanded(
                    child: _VoiceWaveform(
                      active: active,
                      position: _voicePosition,
                      color: fg,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    active ? "$playedSecs/$totalSecs''" : "$totalSecs''",
                    style: TextStyle(
                      color: fg,
                      fontWeight:
                          _voicePlaying ? FontWeight.w700 : FontWeight.w500,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 5),
              ClipRRect(
                borderRadius: BorderRadius.circular(99),
                child: LinearProgressIndicator(
                  minHeight: 3,
                  value: _voiceLoading ? null : progress,
                  backgroundColor: fg.withValues(alpha: 0.18),
                  color: fg.withValues(alpha: 0.78),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _toggleVoice(
    BuildContext context,
    WidgetRef ref,
    VoiceBody c,
  ) async {
    if (_voiceLoading) return;
    final player = _voicePlayer;
    if (player != null && _voicePlaying) {
      await player.pause();
      _voiceCoordinator.release(_voiceToken);
      return;
    }
    if (player != null && _voicePosition > Duration.zero) {
      _voiceCoordinator.claim(_voiceToken);
      await player.resume();
      return;
    }
    await _playVoice(context, ref, c);
  }

  Future<void> _playVoice(
    BuildContext context,
    WidgetRef ref,
    VoiceBody c,
  ) async {
    final messenger = ScaffoldMessenger.of(context);
    _disposeVoicePlayer(releaseCoordinator: false);
    _voiceCoordinator.claim(_voiceToken);
    if (mounted) {
      setState(() {
        _voiceLoading = true;
        _voicePlaying = false;
        _voicePosition = Duration.zero;
        _voiceDuration =
            c.durationMs > 0 ? Duration(milliseconds: c.durationMs) : null;
      });
    }

    final player = AudioPlayer();
    _voicePlayer = player;
    _bindVoicePlayer(player);

    try {
      final file = await _voicePlaybackFile(ref, c);
      await player.setReleaseMode(ReleaseMode.stop);
      await player.play(DeviceFileSource(file.path));
      if (!isSelf) {
        unawaited(
          ref.read(voiceMessageStateRepositoryProvider).markPlayed(_voiceToken),
        );
      }
      if (mounted) {
        setState(() {
          _voiceLoading = false;
          _voicePlaying = true;
        });
      }
    } catch (e) {
      if (identical(_voicePlayer, player)) {
        _disposeVoicePlayer();
      } else {
        unawaited(player.dispose());
      }
      if (!mounted) return;
      setState(_clearVoiceState);
      messenger.showSnackBar(SnackBar(content: Text('播放语音失败：$e')));
    }
  }

  void _bindVoicePlayer(AudioPlayer player) {
    _voiceStateSub = player.onPlayerStateChanged.listen((state) {
      if (!mounted || !identical(_voicePlayer, player)) return;
      setState(() {
        _voicePlaying = state == PlayerState.playing;
        _voiceLoading = false;
      });
    });
    _voicePositionSub = player.onPositionChanged.listen((position) {
      if (!mounted || !identical(_voicePlayer, player)) return;
      setState(() => _voicePosition = position);
    });
    _voiceDurationSub = player.onDurationChanged.listen((duration) {
      if (!mounted || !identical(_voicePlayer, player)) return;
      setState(() => _voiceDuration = duration);
    });
    _voiceCompleteSub = player.onPlayerComplete.listen((_) {
      if (!mounted || !identical(_voicePlayer, player)) return;
      _disposeVoicePlayer();
      setState(_clearVoiceState);
    });
  }

  void _onActiveVoiceChanged(String? token) {
    if (!mounted || token == null || token == _voiceToken) return;
    if (_voicePlayer == null &&
        !_voiceLoading &&
        _voicePosition == Duration.zero) {
      return;
    }
    _disposeVoicePlayer(releaseCoordinator: false);
    setState(_clearVoiceState);
  }

  Future<File> _voicePlaybackFile(WidgetRef ref, VoiceBody c) async {
    final localFile = isSelf ? await _existingLocalFile(c.localPath) : null;
    if (localFile != null) return localFile;
    if (c.objectKey.isEmpty) throw StateError('voice object key is empty');
    return _cachedVoiceFile(ref, c);
  }

  Future<File?> _existingLocalFile(String? path) async {
    if (path == null || path.isEmpty) return null;
    final file = File(path);
    if (!await file.exists()) return null;
    if (await file.length() <= 0) return null;
    return file;
  }

  void _disposeVoicePlayer({
    bool releaseCoordinator = true,
    String? token,
  }) {
    unawaited(_voiceStateSub?.cancel());
    unawaited(_voicePositionSub?.cancel());
    unawaited(_voiceDurationSub?.cancel());
    unawaited(_voiceCompleteSub?.cancel());
    _voiceStateSub = null;
    _voicePositionSub = null;
    _voiceDurationSub = null;
    _voiceCompleteSub = null;

    final player = _voicePlayer;
    _voicePlayer = null;
    if (player != null) unawaited(player.dispose());
    if (releaseCoordinator) {
      _voiceCoordinator.release(token ?? _voiceToken);
    }
  }

  void _clearVoiceState() {
    _voiceLoading = false;
    _voicePlaying = false;
    _voicePosition = Duration.zero;
    _voiceDuration = null;
  }

  Future<File> _cachedVoiceFile(WidgetRef ref, VoiceBody c) async {
    final dir = await getTemporaryDirectory();
    final cacheDir = Directory(p.join(dir.path, 'lumo_voice_cache'));
    await cacheDir.create(recursive: true);

    final file = File(p.join(cacheDir.path, _voiceCacheName(c)));
    if (await file.exists() && await file.length() > 0) return file;

    final info = await ref.read(downloadUrlCacheProvider).resolve(c.objectKey);
    final bytes = await ref.read(fileApiProvider).downloadUrlBytes(info.url);
    if (bytes.isEmpty) throw StateError('voice download is empty');
    await file.writeAsBytes(bytes, flush: true);
    return file;
  }

  String _voiceCacheName(VoiceBody c) {
    final safeKey = c.objectKey.replaceAll(RegExp(r'[^a-zA-Z0-9._-]+'), '_');
    final ext = switch (c.codec) {
      'aac' => '.m4a',
      'opus' => '.opus',
      _ => '.audio',
    };
    return safeKey.endsWith(ext) ? safeKey : '$safeKey$ext';
  }

  Widget _fileRow(
    BuildContext context,
    WidgetRef ref,
    FileBody c,
    Color fg,
  ) {
    final size = c.size;
    final opening = _openingObjectKey == c.objectKey;
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: opening ? null : () => _downloadAndOpenFile(context, ref, c),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 260),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
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
                        style:
                            TextStyle(color: fg, fontWeight: FontWeight.w600),
                      ),
                      if (size != null)
                        Text(
                          _fmtSize(size),
                          style: TextStyle(
                            color: fg.withValues(alpha: 0.7),
                            fontSize: 11,
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                opening
                    ? SizedBox.square(
                        dimension: 16,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: fg,
                        ),
                      )
                    : Icon(Icons.open_in_new, color: fg, size: 17),
              ],
            ),
            if (opening && _openingProgress != null) ...[
              const SizedBox(height: 8),
              ClipRRect(
                borderRadius: BorderRadius.circular(99),
                child: LinearProgressIndicator(
                  minHeight: 3,
                  value: _openingProgress,
                  color: fg,
                  backgroundColor: fg.withValues(alpha: 0.18),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _openVideoPreview(
    BuildContext context,
    WidgetRef ref,
    VideoBody c,
  ) async {
    if (c.objectKey.isEmpty) return;
    final messenger = ScaffoldMessenger.of(context);
    setState(() {
      _openingObjectKey = c.objectKey;
      _openingProgress = null;
    });
    try {
      final file = await _existingLocalFile(c.localPath) ??
          await _cachedDownload(
            ref,
            objectKey: c.objectKey,
            fileName: c.fileName,
            cacheDirName: 'lumo_video_cache',
            variant: 'playback',
          );
      if (Platform.isWindows || Platform.isLinux) {
        final result = await OpenFilex.open(file.path);
        if (result.type != ResultType.done) {
          throw StateError(result.message);
        }
        return;
      }
      if (!context.mounted) return;
      await showDialog<void>(
        context: context,
        barrierColor: Colors.black87,
        builder: (_) => _VideoPreviewDialog(file: file, title: c.fileName),
      );
    } catch (e) {
      if (!mounted) return;
      messenger.showSnackBar(SnackBar(content: Text('视频打开失败：$e')));
    } finally {
      if (mounted && _openingObjectKey == c.objectKey) {
        setState(() {
          _openingObjectKey = null;
          _openingProgress = null;
        });
      }
    }
  }

  Future<void> _downloadAndOpenFile(
    BuildContext context,
    WidgetRef ref,
    FileBody c,
  ) async {
    if (c.objectKey.isEmpty) return;
    final messenger = ScaffoldMessenger.of(context);
    setState(() {
      _openingObjectKey = c.objectKey;
      _openingProgress = null;
    });
    try {
      final file = await _cachedDownload(
        ref,
        objectKey: c.objectKey,
        fileName: c.fileName,
        cacheDirName: 'lumo_file_cache',
      );
      final result = await OpenFilex.open(file.path);
      if (result.type != ResultType.done) {
        throw StateError(result.message);
      }
    } catch (e) {
      if (!mounted) return;
      messenger.showSnackBar(SnackBar(content: Text('文件打开失败：$e')));
    } finally {
      if (mounted && _openingObjectKey == c.objectKey) {
        setState(() {
          _openingObjectKey = null;
          _openingProgress = null;
        });
      }
    }
  }

  Future<File> _cachedDownload(
    WidgetRef ref, {
    required String objectKey,
    required String fileName,
    required String cacheDirName,
    String? variant,
  }) async {
    final dir = await getTemporaryDirectory();
    final cacheDir = Directory(p.join(dir.path, cacheDirName));
    await cacheDir.create(recursive: true);
    final downloadInfo = await ref.read(downloadUrlCacheProvider).resolve(
          objectKey,
          variant: variant,
        );
    final resolvedObjectKey =
        downloadInfo.objectKey.isEmpty ? objectKey : downloadInfo.objectKey;
    final file = File(
        p.join(cacheDir.path, _cacheFileName(resolvedObjectKey, fileName)));
    if (await file.exists() && await file.length() > 0) return file;
    final tmp = File('${file.path}.download');
    if (await tmp.exists()) await tmp.delete();
    void onReceiveProgress(int received, int total) {
      if (!mounted || _openingObjectKey != objectKey) return;
      setState(() {
        _openingProgress =
            total > 0 ? (received / total).clamp(0.0, 1.0).toDouble() : null;
      });
    }

    await ref.read(fileApiProvider).downloadUrlToFile(
          downloadInfo.url,
          tmp.path,
          onReceiveProgress: onReceiveProgress,
        );
    if (await file.exists()) await file.delete();
    return tmp.rename(file.path);
  }

  String _cacheFileName(String objectKey, String fileName) {
    final safeKey = objectKey.replaceAll(RegExp(r'[^a-zA-Z0-9._-]+'), '_');
    final ext = p.extension(fileName);
    if (ext.isEmpty || safeKey.toLowerCase().endsWith(ext.toLowerCase())) {
      return safeKey;
    }
    return '$safeKey$ext';
  }

  Future<void> _showActions(BuildContext context) async {
    if (message.isRevoked || message.isNotification) return;
    final actions = _availableActions();
    if (actions.isEmpty) return;
    final action = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: actions
              .map(
                (item) => ListTile(
                  leading: Icon(item.icon),
                  title: Text(item.label),
                  onTap: () => Navigator.pop(sheetContext, item.value),
                ),
              )
              .toList(),
        ),
      ),
    );
    if (!mounted || action == null) return;
    switch (action) {
      case 'copy':
        await _copyMessage(this.context);
      case 'quote':
        widget.onQuote?.call(message);
      case 'forward':
        await _forwardMessage(this.context, merge: false);
      case 'mergeForward':
        if (widget.onMergeForward != null) {
          widget.onMergeForward?.call(message);
        } else {
          await _forwardMessage(this.context, merge: true);
        }
      case 'revoke':
        await _revokeMessage(this.context);
      case 'deleteLocal':
        await _deleteLocal(this.context);
    }
  }

  List<_MessageAction> _availableActions() {
    final actions = <_MessageAction>[];
    if (_canCopy) {
      actions.add(
        const _MessageAction(
          value: 'copy',
          label: '复制',
          icon: Icons.copy_outlined,
        ),
      );
    }
    if (widget.onQuote != null) {
      actions.add(
        const _MessageAction(
          value: 'quote',
          label: '引用回复',
          icon: Icons.format_quote_outlined,
        ),
      );
    }
    if (_canForward) {
      actions.addAll(const [
        _MessageAction(
          value: 'forward',
          label: '转发',
          icon: Icons.shortcut_outlined,
        ),
        _MessageAction(
          value: 'mergeForward',
          label: '多选合并转发',
          icon: Icons.checklist_outlined,
        ),
      ]);
    }
    if (_canRevoke) {
      actions.add(
        const _MessageAction(
          value: 'revoke',
          label: '撤回',
          icon: Icons.undo_outlined,
        ),
      );
    }
    actions.add(
      const _MessageAction(
        value: 'deleteLocal',
        label: '删除本地',
        icon: Icons.delete_outline,
      ),
    );
    return actions;
  }

  bool get _canCopy =>
      message.content is TextBody ||
      message.content is CustomBody ||
      _canForward;

  bool get _canForward => isForwardableMessage(message);

  bool get _canRevoke =>
      isSelf && message.seq != null && message.status.isAcked;

  Future<void> _copyMessage(BuildContext context) async {
    final raw = switch (message.content) {
      TextBody(:final text) => text,
      CustomBody() => customPreview(message.content as CustomBody),
      _ => messagePreview(message.content),
    };
    await Clipboard.setData(ClipboardData(text: raw));
    if (context.mounted) _toast(context, '已复制');
  }

  Future<void> _forwardMessage(
    BuildContext context, {
    required bool merge,
  }) async {
    final target = await _pickTargetConversation(context);
    if (target == null || !context.mounted) return;
    final content = merge
        ? mergeForwardContent(message)
        : forwardableContent(message.content);
    try {
      await ref
          .read(messageRepositoryProvider)
          .sendContent(target.convId, content);
      if (context.mounted) _toast(context, '已转发给 ${target.title}');
    } catch (e) {
      if (context.mounted) _toast(context, '转发失败：$e');
    }
  }

  Future<void> _showMergeForwardDetail(
    BuildContext context,
    MergeForwardPayload merge,
  ) {
    return showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (sheetContext) => SafeArea(
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.sizeOf(sheetContext).height * 0.76,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
                child: Row(
                  children: [
                    const Icon(Icons.library_books_outlined),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        merge.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 17,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const Divider(height: 1),
              Flexible(
                child: ListView.separated(
                  shrinkWrap: true,
                  itemCount: merge.items.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (_, index) {
                    final item = merge.items[index];
                    final time = TimeFmt.messageClock(item.sendTime);
                    return ListTile(
                      leading: CircleAvatar(
                        backgroundColor: LumoColors.primarySoft,
                        foregroundColor: LumoColors.primary,
                        child: Icon(_mergeItemIcon(item.kind), size: 20),
                      ),
                      title: Row(
                        children: [
                          Expanded(
                            child: Text(
                              item.senderName.isEmpty
                                  ? '未知发送者'
                                  : item.senderName,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style:
                                  const TextStyle(fontWeight: FontWeight.w600),
                            ),
                          ),
                          if (time.isNotEmpty) ...[
                            const SizedBox(width: 8),
                            Text(
                              time,
                              style: const TextStyle(
                                fontSize: 12,
                                color: LumoColors.textSecondary,
                              ),
                            ),
                          ],
                        ],
                      ),
                      subtitle: Padding(
                        padding: const EdgeInsets.only(top: 3),
                        child: Text(
                          item.preview,
                          maxLines: 3,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
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

  IconData _mergeItemIcon(String kind) => switch (kind) {
        'image' => Icons.image_outlined,
        'voice' => Icons.keyboard_voice_outlined,
        'file' => Icons.insert_drive_file_outlined,
        'video' => Icons.play_circle_outline,
        'custom' => Icons.extension_outlined,
        _ => Icons.chat_bubble_outline,
      };

  Future<Conversation?> _pickTargetConversation(BuildContext context) async {
    final convs = ref.read(conversationsProvider).valueOrNull ?? const [];
    final targets = convs.where((conv) => !conv.isSystem).toList();
    if (targets.isEmpty) {
      _toast(context, '没有可转发的会话');
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

  Future<void> _revokeMessage(BuildContext context) async {
    final seq = message.seq;
    if (seq == null) return;
    final confirm = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('撤回消息'),
        content: const Text('撤回后对方也会看到这条消息已撤回。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('撤回'),
          ),
        ],
      ),
    );
    if (confirm != true || !context.mounted) return;
    try {
      await ref.read(messageRepositoryProvider).revoke(message.convId, seq);
      if (context.mounted) _toast(context, '已撤回');
    } catch (e) {
      if (context.mounted) _toast(context, '撤回失败：$e');
    }
  }

  Future<void> _deleteLocal(BuildContext context) async {
    try {
      await ref
          .read(messageRepositoryProvider)
          .deleteLocal(message.clientMsgId);
      if (context.mounted) _toast(context, '已删除本地消息');
    } catch (e) {
      if (context.mounted) _toast(context, '删除失败：$e');
    }
  }

  void _toast(BuildContext context, String text) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
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

  Widget _centerChip(String label) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Center(
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: LumoColors.surfaceAlt,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text(
              label,
              style: const TextStyle(
                fontSize: 12,
                color: LumoColors.textSecondary,
              ),
            ),
          ),
        ),
      );

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

  String _fmtDuration(int ms) => _formatDuration(Duration(milliseconds: ms));
}

class _VideoPreviewDialog extends StatefulWidget {
  const _VideoPreviewDialog({required this.file, required this.title});

  final File file;
  final String title;

  @override
  State<_VideoPreviewDialog> createState() => _VideoPreviewDialogState();
}

class _VideoPreviewDialogState extends State<_VideoPreviewDialog> {
  VideoPlayerController? _controller;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _init();
  }

  @override
  void dispose() {
    _controller?.removeListener(_onTick);
    _controller?.dispose();
    super.dispose();
  }

  Future<void> _init() async {
    final controller = VideoPlayerController.file(widget.file);
    try {
      await controller.initialize();
      controller.addListener(_onTick);
      await controller.play();
      if (!mounted) {
        await controller.dispose();
        return;
      }
      setState(() {
        _controller = controller;
        _loading = false;
      });
    } catch (e) {
      await controller.dispose();
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = e.toString();
      });
    }
  }

  void _onTick() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final controller = _controller;
    final initialized = controller?.value.isInitialized ?? false;
    final duration = controller?.value.duration ?? Duration.zero;
    final position = controller?.value.position ?? Duration.zero;
    final progress = duration.inMilliseconds > 0
        ? (position.inMilliseconds / duration.inMilliseconds)
            .clamp(0.0, 1.0)
            .toDouble()
        : 0.0;

    return Dialog.fullscreen(
      backgroundColor: Colors.black,
      child: Stack(
        children: [
          Positioned.fill(
            child: Center(
              child: _loading
                  ? const CircularProgressIndicator(color: Colors.white)
                  : _error != null
                      ? Padding(
                          padding: const EdgeInsets.all(24),
                          child: Text(
                            '视频播放失败：$_error',
                            style: const TextStyle(color: Colors.white70),
                            textAlign: TextAlign.center,
                          ),
                        )
                      : initialized
                          ? AspectRatio(
                              aspectRatio: controller!.value.aspectRatio == 0
                                  ? 16 / 9
                                  : controller.value.aspectRatio,
                              child: VideoPlayer(controller),
                            )
                          : const SizedBox.shrink(),
            ),
          ),
          Positioned(
            top: 18,
            left: 18,
            right: 18,
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    widget.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                IconButton.filled(
                  tooltip: '关闭',
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close),
                ),
              ],
            ),
          ),
          if (initialized)
            Positioned(
              left: 18,
              right: 18,
              bottom: 20,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(99),
                    child: LinearProgressIndicator(
                      minHeight: 4,
                      value: progress,
                      color: Colors.white,
                      backgroundColor: Colors.white24,
                    ),
                  ),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      IconButton.filled(
                        tooltip: controller!.value.isPlaying ? '暂停' : '播放',
                        onPressed: () {
                          controller.value.isPlaying
                              ? controller.pause()
                              : controller.play();
                        },
                        icon: Icon(controller.value.isPlaying
                            ? Icons.pause
                            : Icons.play_arrow),
                      ),
                      const SizedBox(width: 10),
                      Text(
                        '${_formatDuration(position)} / ${_formatDuration(duration)}',
                        style: const TextStyle(color: Colors.white70),
                      ),
                    ],
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

String _formatDuration(Duration duration) {
  final seconds = duration.inSeconds.clamp(0, 24 * 60 * 60);
  final minutes = seconds ~/ 60;
  final remain = seconds % 60;
  return '$minutes:${remain.toString().padLeft(2, '0')}';
}

class _VoiceWaveform extends StatelessWidget {
  const _VoiceWaveform({
    required this.active,
    required this.position,
    required this.color,
  });

  final bool active;
  final Duration position;
  final Color color;

  @override
  Widget build(BuildContext context) {
    const idleBars = [7.0, 12.0, 16.0, 11.0, 18.0, 13.0];
    final phase = (position.inMilliseconds ~/ 160) % idleBars.length;
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: List.generate(idleBars.length, (i) {
        final height = active
            ? idleBars[(i + phase) % idleBars.length]
            : idleBars[i] * 0.72;
        return AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          margin: const EdgeInsets.symmetric(horizontal: 1.7),
          width: 3,
          height: height,
          decoration: BoxDecoration(
            color: color.withValues(alpha: active ? 0.95 : 0.62),
            borderRadius: BorderRadius.circular(99),
          ),
        );
      }),
    );
  }
}

class _UnreadVoiceDot extends StatelessWidget {
  const _UnreadVoiceDot();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 7,
      height: 7,
      decoration: const BoxDecoration(
        color: LumoColors.danger,
        shape: BoxShape.circle,
      ),
    );
  }
}

class _MessageAction {
  const _MessageAction({
    required this.value,
    required this.label,
    required this.icon,
  });

  final String value;
  final String label;
  final IconData icon;
}
