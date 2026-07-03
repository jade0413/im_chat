import 'dart:async';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';

import '../../../app/providers.dart';
import '../../../core/theme/lumo_colors.dart';
import '../../../data/attachment_sender.dart';
import '../../../data/media_mime.dart';
import '../../../data/models/chat_message.dart';
import '../../../data/models/message_payloads.dart';
import '../../../data/remote/rest/api_client.dart';
import '../../../data/repositories/conversation_repository.dart';
import '../../../data/repositories/message_repository.dart';
import 'mention_picker.dart';

/// 输入栏：文本 + 发送 + 草稿（#13）+ 表情面板 + 图片/文件直传 + 群 @提及。
class InputBar extends ConsumerStatefulWidget {
  const InputBar({
    super.key,
    required this.convId,
    this.quote,
    this.onClearQuote,
  });
  final String convId;
  final ChatMessage? quote;
  final VoidCallback? onClearQuote;

  @override
  ConsumerState<InputBar> createState() => _InputBarState();
}

/// 记录一次插入的 @提及：显示名 + 目标 userId（'-1'=所有人）。
class _Mention {
  const _Mention(this.display, this.userId);
  final String display;
  final String userId;
}

class _InputBarState extends ConsumerState<InputBar> {
  final _controller = TextEditingController();
  final _focus = FocusNode();
  bool _hasText = false;
  bool _showEmoji = false;
  bool _uploading = false;
  String? _uploadLabel;
  double? _uploadProgress;
  bool _claiming = false;
  bool _recording = false;
  DateTime? _recordStartedAt;
  String? _recordingPath;
  Timer? _recordTimer;
  Duration _recordElapsed = Duration.zero;
  final AudioRecorder _recorder = AudioRecorder();
  late final MessageRepository _messages;
  late final ConversationRepository _convs;

  // 群 @提及状态
  bool _isGroup = false;
  String? _groupId;
  String _lastText = '';
  bool _mentionBusy = false;
  final List<_Mention> _mentions = [];

  @override
  void initState() {
    super.initState();
    _messages = ref.read(messageRepositoryProvider);
    _convs = ref.read(conversationRepositoryProvider);
    _controller.addListener(_onTextChanged);
    // 恢复草稿
    Future.microtask(() async {
      final conv = await _convs.get(widget.convId);
      final draft = conv?.draft;
      if (mounted &&
          draft != null &&
          draft.isNotEmpty &&
          _controller.text.isEmpty) {
        _controller.text = draft;
        _lastText = draft;
      }
    });
  }

  @override
  void dispose() {
    _convs.saveDraft(widget.convId, _controller.text.trim());
    _recordTimer?.cancel();
    unawaited(_recorder.dispose());
    _controller.dispose();
    _focus.dispose();
    super.dispose();
  }

  void _onTextChanged() {
    final text = _controller.text;
    final has = text.trim().isNotEmpty;
    if (has != _hasText) setState(() => _hasText = has);

    // 群聊内新键入 '@' → 打开成员选择器
    if (_isGroup && !_mentionBusy && text.length == _lastText.length + 1) {
      final pos = _controller.selection.baseOffset;
      if (pos > 0 && pos <= text.length && text[pos - 1] == '@') {
        _openMention(atIndex: pos - 1);
      }
    }
    _lastText = text;
  }

  Future<void> _openMention({int? atIndex}) async {
    final gid = _groupId;
    if (gid == null || _mentionBusy) return;
    _mentionBusy = true;
    final selfId = ref.read(authControllerProvider).user?.id ?? '0';
    final picked =
        await showMentionPicker(context, groupId: gid, selfUserId: selfId);
    if (picked != null) {
      _insertMention(picked, atIndex: atIndex);
    }
    _mentionBusy = false;
  }

  /// 插入 '@显示名 '；atIndex 非空时替换该处已键入的 '@'（避免出现双 @）。
  void _insertMention(MentionResult pick, {int? atIndex}) {
    final token = '@${pick.display} ';
    final text = _controller.text;
    int start;
    int end;
    if (atIndex != null && atIndex < text.length && text[atIndex] == '@') {
      start = atIndex;
      end = atIndex + 1;
    } else {
      final sel = _controller.selection;
      start = sel.isValid ? sel.baseOffset : text.length;
      end = sel.isValid ? sel.extentOffset : text.length;
    }
    final newText = text.replaceRange(start, end, token);
    _controller.value = TextEditingValue(
      text: newText,
      selection: TextSelection.collapsed(offset: start + token.length),
    );
    _lastText = newText;
    _mentions.add(_Mention(pick.display, pick.userId));
    _focus.requestFocus();
  }

  /// 收集仍存在于文本中的 @提及 userId（用户可能删掉了某个 @）。
  List<String> _collectAtUserIds(String text) {
    final ids = <String>{};
    for (final m in _mentions) {
      if (text.contains('@${m.display}')) ids.add(m.userId);
    }
    return ids.toList();
  }

  void _send() {
    final text = _controller.text.trim();
    if (text.isEmpty) return;
    final atIds = _collectAtUserIds(text);
    final quote = widget.quote;
    if (quote == null) {
      _messages.sendText(widget.convId, text, atUserIds: atIds);
    } else {
      _messages.sendContent(
        widget.convId,
        quoteReplyContent(quoted: quote, text: text),
      );
      widget.onClearQuote?.call();
    }
    _controller.clear();
    _lastText = '';
    _mentions.clear();
    _convs.saveDraft(widget.convId, null);
    _focus.requestFocus();
  }

  void _toggleEmoji() {
    setState(() => _showEmoji = !_showEmoji);
    if (_showEmoji) {
      _focus.unfocus();
    } else {
      _focus.requestFocus();
    }
  }

  void _insertText(String s) {
    final text = _controller.text;
    final sel = _controller.selection;
    final start = sel.isValid ? sel.baseOffset : text.length;
    final end = sel.isValid ? sel.extentOffset : text.length;
    final newText = text.replaceRange(start, end, s);
    _controller.value = TextEditingValue(
      text: newText,
      selection: TextSelection.collapsed(offset: start + s.length),
    );
    _lastText = newText;
  }

  Future<void> _pickImage() async {
    if (_uploading) return;
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.image,
        withData: true,
        allowMultiple: true,
      );
      final files = result?.files ?? const <PlatformFile>[];
      if (files.isEmpty) return;
      await _runUpload(() async {
        for (final file in files) {
          final bytes = await _readPickedFileBytes(file);
          if (bytes == null) {
            _toast('无法读取图片内容：${file.name}');
            continue;
          }
          final imageSize = await _decodeImageSize(bytes);
          await ref.read(attachmentSenderProvider).sendImage(
                widget.convId,
                bytes: bytes,
                fileName: file.name,
                mime: _mimeFromName(file.name),
                localPath: file.path,
                width: imageSize?.width.round(),
                height: imageSize?.height.round(),
                onProgress: _onUploadProgress,
              );
        }
      });
    } catch (e) {
      _toast('图片发送失败：${describeApiError(e)}');
    }
  }

  Future<void> _pickFile() async {
    if (_uploading) return;
    try {
      final result = await FilePicker.platform.pickFiles(
        withData: true,
        allowMultiple: true,
      );
      final files = result?.files ?? const <PlatformFile>[];
      if (files.isEmpty) return;
      await _runUpload(() async {
        for (final file in files) {
          final bytes = await _readPickedFileBytes(file);
          if (bytes == null) {
            _toast('无法读取文件内容：${file.name}');
            continue;
          }
          final mime = _mimeFromName(file.name);
          final sender = ref.read(attachmentSenderProvider);
          if (mime.startsWith('video/')) {
            await sender.sendVideo(
              widget.convId,
              bytes: bytes,
              fileName: file.name,
              mime: mime,
              localPath: file.path,
              onProgress: _onUploadProgress,
            );
          } else {
            await sender.sendFile(
              widget.convId,
              bytes: bytes,
              fileName: file.name,
              mime: mime,
              onProgress: _onUploadProgress,
            );
          }
        }
      });
    } catch (e) {
      _toast('文件发送失败：${describeApiError(e)}');
    }
  }

  Future<void> _startRecording() async {
    if (_uploading || _recording) return;
    try {
      final allowed = await _recorder.hasPermission();
      if (!allowed) {
        _toast('没有麦克风权限');
        return;
      }
      final dir = await getTemporaryDirectory();
      final fileName =
          'lumo_voice_${DateTime.now().millisecondsSinceEpoch}.m4a';
      final path = p.join(dir.path, fileName);
      await _recorder.start(
        const RecordConfig(
          encoder: AudioEncoder.aacLc,
          bitRate: 32000,
          sampleRate: 16000,
          numChannels: 1,
        ),
        path: path,
      );
      _focus.unfocus();
      _recordTimer?.cancel();
      final startedAt = DateTime.now();
      setState(() {
        _showEmoji = false;
        _recording = true;
        _recordStartedAt = startedAt;
        _recordingPath = path;
        _recordElapsed = Duration.zero;
      });
      _recordTimer = Timer.periodic(const Duration(milliseconds: 250), (_) {
        if (!mounted || !_recording || _recordStartedAt == null) return;
        setState(() => _recordElapsed = DateTime.now().difference(startedAt));
      });
    } catch (e) {
      _toast('开始录音失败：${describeApiError(e)}');
    }
  }

  Future<void> _cancelRecording() async {
    if (!_recording) return;
    try {
      await _recorder.cancel();
    } catch (_) {
      final path = _recordingPath;
      if (path != null) {
        unawaited(File(path).delete().catchError((_) => File(path)));
      }
    } finally {
      _recordTimer?.cancel();
      if (mounted) {
        setState(() {
          _recording = false;
          _recordStartedAt = null;
          _recordingPath = null;
          _recordElapsed = Duration.zero;
        });
      }
    }
  }

  Future<void> _stopAndSendVoice() async {
    if (!_recording || _uploading) return;
    final startedAt = _recordStartedAt;
    final fallbackPath = _recordingPath;
    _recordTimer?.cancel();
    try {
      final path = await _recorder.stop() ?? fallbackPath;
      final durationMs = startedAt == null
          ? _recordElapsed.inMilliseconds
          : DateTime.now().difference(startedAt).inMilliseconds;
      if (mounted) {
        setState(() {
          _recording = false;
          _recordStartedAt = null;
          _recordingPath = null;
          _recordElapsed = Duration.zero;
        });
      }
      if (path == null || path.isEmpty) {
        _toast('录音文件不存在');
        return;
      }
      final file = File(path);
      if (!await file.exists()) {
        _toast('录音文件不存在');
        return;
      }
      if (durationMs < 700) {
        unawaited(file.delete().catchError((_) => file));
        _toast('语音太短');
        return;
      }
      final bytes = await file.readAsBytes();
      await _runUpload(() => ref.read(attachmentSenderProvider).sendVoice(
            widget.convId,
            bytes: bytes,
            fileName: p.basename(path),
            mime: 'audio/mp4',
            durationMs: durationMs,
            codec: 'aac',
            localPath: path,
            onProgress: _onUploadProgress,
          ));
    } catch (e) {
      if (mounted) {
        setState(() {
          _recording = false;
          _recordStartedAt = null;
          _recordingPath = null;
          _recordElapsed = Duration.zero;
        });
      }
      _toast('语音发送失败：${describeApiError(e)}');
    }
  }

  Future<Uint8List?> _readPickedFileBytes(PlatformFile file) async {
    final bytes = file.bytes;
    if (bytes != null) return bytes;
    final path = file.path;
    if (path == null || path.isEmpty) return null;
    return File(path).readAsBytes();
  }

  Future<ui.Size?> _decodeImageSize(Uint8List bytes) async {
    try {
      final codec = await ui.instantiateImageCodec(bytes);
      final frame = await codec.getNextFrame();
      final image = frame.image;
      final size = ui.Size(image.width.toDouble(), image.height.toDouble());
      image.dispose();
      return size;
    } catch (_) {
      return null;
    }
  }

  Future<void> _runUpload(Future<void> Function() task) async {
    setState(() {
      _uploading = true;
      _uploadLabel = '准备上传';
      _uploadProgress = null;
    });
    try {
      await task();
    } finally {
      if (mounted) {
        setState(() {
          _uploading = false;
          _uploadLabel = null;
          _uploadProgress = null;
        });
      }
    }
  }

  void _onUploadProgress(AttachmentTransferProgress progress) {
    if (!mounted) return;
    setState(() {
      _uploadLabel = progress.label;
      _uploadProgress = progress.fraction;
    });
  }

  Future<void> _claimCsConversation() async {
    if (_claiming) return;
    setState(() => _claiming = true);
    try {
      await ref.read(csApiProvider).claim(widget.convId);
      final current = ref.read(conversationProvider(widget.convId)).valueOrNull;
      if (current != null) {
        await ref
            .read(conversationRepositoryProvider)
            .save(current.copyWith(csStatus: '2'));
      }
      ref.invalidate(csConversationsProvider);
      _toast('已认领会话');
    } catch (e) {
      _toast('认领失败：$e');
    } finally {
      if (mounted) setState(() => _claiming = false);
    }
  }

  void _toast(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    // 会话类型（决定是否显示 @ 入口）。
    final conv = ref.watch(conversationProvider(widget.convId)).valueOrNull;
    _isGroup = conv?.isGroup ?? false;
    _groupId = conv?.groupId;
    if (conv?.isSystem ?? false) {
      return const _DisabledInputNotice(text: '系统通知会话，不可回复');
    }
    if (conv?.isCs ?? false) {
      final status = conv?.csStatus;
      if (status != '2') {
        final agentStatus =
            ref.watch(authControllerProvider).user?.agentStatus ?? 0;
        final canClaim = status == '1' && agentStatus == 1;
        return _DisabledInputNotice(
          text: _csDisabledText(status, agentStatus),
          action: status == '1'
              ? FilledButton.icon(
                  onPressed:
                      canClaim && !_claiming ? _claimCsConversation : null,
                  icon: _claiming
                      ? const SizedBox.square(
                          dimension: 16,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Icon(Icons.person_add_alt_1_rounded),
                  label: Text(_claiming ? '认领中' : '认领会话'),
                )
              : null,
        );
      }
    }

    return Container(
      decoration: BoxDecoration(
        color: Theme.of(context).appBarTheme.backgroundColor,
        border: const Border(
            top: BorderSide(color: LumoColors.divider, width: 0.6)),
      ),
      padding: const EdgeInsets.fromLTRB(8, 8, 8, 10),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (_recording)
              _RecordingBar(
                elapsed: _recordElapsed,
                onCancel: _cancelRecording,
                onSend: _stopAndSendVoice,
              )
            else
              Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (widget.quote != null)
                    _QuoteDraftBar(
                      message: widget.quote!,
                      onClear: widget.onClearQuote,
                    ),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      IconButton(
                        onPressed: _toggleEmoji,
                        icon: Icon(_showEmoji
                            ? Icons.keyboard_outlined
                            : Icons.emoji_emotions_outlined),
                        color: LumoColors.textSecondary,
                      ),
                      if (_isGroup)
                        IconButton(
                          tooltip: '提醒谁看',
                          onPressed: () => _openMention(),
                          icon: const Icon(Icons.alternate_email),
                          color: LumoColors.textSecondary,
                        ),
                      Expanded(
                        child: CallbackShortcuts(
                          bindings: {
                            const SingleActivator(LogicalKeyboardKey.enter):
                                _send,
                          },
                          child: TextField(
                            controller: _controller,
                            focusNode: _focus,
                            minLines: 1,
                            maxLines: 5,
                            textInputAction: TextInputAction.newline,
                            onTap: () {
                              if (_showEmoji) {
                                setState(() => _showEmoji = false);
                              }
                            },
                            decoration: const InputDecoration(
                              hintText: '输入消息…',
                              contentPadding: EdgeInsets.symmetric(
                                  horizontal: 14, vertical: 10),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 4),
                      if (_uploading)
                        const Padding(
                          padding: EdgeInsets.all(12),
                          child: SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          ),
                        )
                      else if (!_hasText) ...[
                        IconButton(
                          onPressed: _startRecording,
                          icon: const Icon(Icons.mic_none),
                          color: LumoColors.textSecondary,
                        ),
                        IconButton(
                          onPressed: _pickImage,
                          icon: const Icon(Icons.image_outlined),
                          color: LumoColors.textSecondary,
                        ),
                        IconButton(
                          onPressed: _pickFile,
                          icon: const Icon(Icons.add_circle_outline),
                          color: LumoColors.textSecondary,
                        ),
                      ] else
                        _SendButton(onTap: _send),
                    ],
                  ),
                ],
              ),
            if (_uploading && !_recording)
              _UploadProgressBar(
                label: _uploadLabel ?? '上传中',
                progress: _uploadProgress,
              ),
            if (_showEmoji) _EmojiPanel(onPick: _insertText),
          ],
        ),
      ),
    );
  }

  String _mimeFromName(String name) => mediaMimeFromFileName(name);

  String _csDisabledText(String? csStatus, int agentStatus) {
    if (csStatus == '1') {
      if (agentStatus == 1) return '认领会话后可查看记录并回复访客';
      if (agentStatus == 2) return '当前忙碌中，切换为在线后可认领新访客';
      return '当前离线，切换为在线后可认领新访客';
    }
    if (csStatus == '3') return '会话已结单，不能继续发送消息';
    return '客服会话状态异常，暂不可回复';
  }
}

class _UploadProgressBar extends StatelessWidget {
  const _UploadProgressBar({required this.label, required this.progress});

  final String label;
  final double? progress;

  @override
  Widget build(BuildContext context) {
    final percent =
        progress == null ? '' : ' ${(progress! * 100).clamp(0, 100).round()}%';
    return Padding(
      padding: const EdgeInsets.fromLTRB(48, 6, 48, 0),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            '$label$percent',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: LumoColors.textSecondary,
              fontSize: 11,
            ),
          ),
          const SizedBox(height: 4),
          ClipRRect(
            borderRadius: BorderRadius.circular(99),
            child: LinearProgressIndicator(
              minHeight: 3,
              value: progress,
            ),
          ),
        ],
      ),
    );
  }
}

class _QuoteDraftBar extends StatelessWidget {
  const _QuoteDraftBar({required this.message, this.onClear});

  final ChatMessage message;
  final VoidCallback? onClear;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(48, 0, 48, 8),
      child: Container(
        padding: const EdgeInsets.fromLTRB(10, 8, 6, 8),
        decoration: BoxDecoration(
          color: LumoColors.surfaceAlt,
          borderRadius: BorderRadius.circular(8),
          border: const Border(
            left: BorderSide(color: LumoColors.primary, width: 3),
          ),
        ),
        child: Row(
          children: [
            Expanded(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    message.sender.nickname,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: LumoColors.primary,
                      fontWeight: FontWeight.w600,
                      fontSize: 12,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    messagePreview(message.content),
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
            IconButton(
              tooltip: '取消引用',
              onPressed: onClear,
              icon: const Icon(Icons.close_rounded),
              iconSize: 18,
              visualDensity: VisualDensity.compact,
            ),
          ],
        ),
      ),
    );
  }
}

class _RecordingBar extends StatelessWidget {
  const _RecordingBar({
    required this.elapsed,
    required this.onCancel,
    required this.onSend,
  });

  final Duration elapsed;
  final VoidCallback onCancel;
  final VoidCallback onSend;

  @override
  Widget build(BuildContext context) {
    final secs = elapsed.inSeconds.clamp(0, 599);
    final label =
        '${(secs ~/ 60).toString().padLeft(2, '0')}:${(secs % 60).toString().padLeft(2, '0')}';
    return Row(
      children: [
        const Icon(Icons.mic, color: LumoColors.danger),
        const SizedBox(width: 8),
        Expanded(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: const TextStyle(
                  color: LumoColors.ink,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 5),
              const LinearProgressIndicator(minHeight: 3),
            ],
          ),
        ),
        const SizedBox(width: 8),
        IconButton(
          tooltip: '取消',
          onPressed: onCancel,
          icon: const Icon(Icons.close),
        ),
        IconButton.filled(
          tooltip: '发送',
          onPressed: onSend,
          icon: const Icon(Icons.send),
        ),
      ],
    );
  }
}

class _DisabledInputNotice extends StatelessWidget {
  const _DisabledInputNotice({required this.text, this.action});

  final String text;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Theme.of(context).appBarTheme.backgroundColor,
        border: const Border(
            top: BorderSide(color: LumoColors.divider, width: 0.6)),
      ),
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
      child: SafeArea(
        top: false,
        child: Row(
          children: [
            Expanded(
              child: Text(
                text,
                style: const TextStyle(color: LumoColors.textSecondary),
              ),
            ),
            if (action != null) ...[
              const SizedBox(width: 10),
              action!,
            ],
          ],
        ),
      ),
    );
  }
}

/// 常用表情面板（unicode，直接插入文本；跨端一致、零依赖）。
class _EmojiPanel extends StatelessWidget {
  const _EmojiPanel({required this.onPick});
  final void Function(String) onPick;

  static const _emojis = [
    '😀',
    '😁',
    '😂',
    '🤣',
    '😊',
    '😍',
    '😘',
    '😜',
    '🤔',
    '🙂',
    '😉',
    '😎',
    '😢',
    '😭',
    '😡',
    '😱',
    '👍',
    '👎',
    '👏',
    '🙏',
    '💪',
    '🤝',
    '👌',
    '✌️',
    '❤️',
    '💔',
    '💯',
    '🔥',
    '🎉',
    '✨',
    '⭐',
    '🌟',
    '😴',
    '🤗',
    '😅',
    '😇',
    '🥰',
    '😋',
    '🤩',
    '😳',
    '🍺',
    '☕',
    '🎂',
    '🌹',
    '🐶',
    '🐱',
    '🌈',
    '☀️',
  ];

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 220,
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 8),
      child: GridView.builder(
        gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
          maxCrossAxisExtent: 48,
          childAspectRatio: 1,
        ),
        itemCount: _emojis.length,
        itemBuilder: (_, i) => InkWell(
          borderRadius: BorderRadius.circular(8),
          onTap: () => onPick(_emojis[i]),
          child: Center(
            child: Text(_emojis[i], style: const TextStyle(fontSize: 24)),
          ),
        ),
      ),
    );
  }
}

class _SendButton extends StatelessWidget {
  const _SendButton({required this.onTap});
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 4, bottom: 2),
      child: Material(
        color: LumoColors.primary,
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: onTap,
          child: const Padding(
            padding: EdgeInsets.all(10),
            child: Icon(Icons.send_rounded, color: Colors.white, size: 20),
          ),
        ),
      ),
    );
  }
}
