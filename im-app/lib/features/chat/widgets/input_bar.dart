import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../../../app/providers.dart';
import '../../../core/theme/lumo_colors.dart';
import '../../../data/repositories/conversation_repository.dart';
import '../../../data/repositories/message_repository.dart';
import 'mention_picker.dart';

/// 输入栏：文本 + 发送 + 草稿（#13）+ 表情面板 + 图片/文件直传 + 群 @提及。
class InputBar extends ConsumerStatefulWidget {
  const InputBar({super.key, required this.convId});
  final String convId;

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
    _messages.sendText(widget.convId, text, atUserIds: atIds);
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
      final picked = await ImagePicker().pickImage(
        source: ImageSource.gallery,
        imageQuality: 85,
      );
      if (picked == null) return;
      final bytes = await picked.readAsBytes();
      await _runUpload(() => ref.read(attachmentSenderProvider).sendImage(
            widget.convId,
            bytes: bytes,
            fileName: picked.name,
            mime: picked.mimeType ?? _mimeFromName(picked.name),
            localPath: picked.path,
          ));
    } catch (e) {
      _toast('图片发送失败：$e');
    }
  }

  Future<void> _pickFile() async {
    if (_uploading) return;
    try {
      final result = await FilePicker.platform.pickFiles(withData: true);
      final files = result?.files ?? const <PlatformFile>[];
      if (files.isEmpty) return;
      final file = files.first;
      final bytes = file.bytes;
      if (bytes == null) {
        _toast('无法读取文件内容');
        return;
      }
      await _runUpload(() => ref.read(attachmentSenderProvider).sendFile(
            widget.convId,
            bytes: bytes,
            fileName: file.name,
            mime: _mimeFromName(file.name),
          ));
    } catch (e) {
      _toast('文件发送失败：$e');
    }
  }

  Future<void> _runUpload(Future<void> Function() task) async {
    setState(() => _uploading = true);
    try {
      await task();
    } finally {
      if (mounted) setState(() => _uploading = false);
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
                      const SingleActivator(LogicalKeyboardKey.enter): _send,
                    },
                    child: TextField(
                      controller: _controller,
                      focusNode: _focus,
                      minLines: 1,
                      maxLines: 5,
                      textInputAction: TextInputAction.newline,
                      onTap: () {
                        if (_showEmoji) setState(() => _showEmoji = false);
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
            if (_showEmoji) _EmojiPanel(onPick: _insertText),
          ],
        ),
      ),
    );
  }

  String _mimeFromName(String name) {
    final ext = name.contains('.') ? name.split('.').last.toLowerCase() : '';
    switch (ext) {
      case 'jpg':
      case 'jpeg':
        return 'image/jpeg';
      case 'png':
        return 'image/png';
      case 'gif':
        return 'image/gif';
      case 'webp':
        return 'image/webp';
      case 'heic':
        return 'image/heic';
      case 'mp4':
        return 'video/mp4';
      case 'mov':
        return 'video/quicktime';
      case 'pdf':
        return 'application/pdf';
      case 'doc':
        return 'application/msword';
      case 'docx':
        return 'application/vnd.openxmlformats-officedocument'
            '.wordprocessingml.document';
      case 'xls':
        return 'application/vnd.ms-excel';
      case 'xlsx':
        return 'application/vnd.openxmlformats-officedocument'
            '.spreadsheetml.sheet';
      case 'zip':
        return 'application/zip';
      case 'txt':
        return 'text/plain';
      default:
        return 'application/octet-stream';
    }
  }
}

/// 常用表情面板（unicode，直接插入文本；跨端一致、零依赖）。
class _EmojiPanel extends StatelessWidget {
  const _EmojiPanel({required this.onPick});
  final void Function(String) onPick;

  static const _emojis = [
    '😀', '😁', '😂', '🤣', '😊', '😍', '😘', '😜',
    '🤔', '🙂', '😉', '😎', '😢', '😭', '😡', '😱',
    '👍', '👎', '👏', '🙏', '💪', '🤝', '👌', '✌️',
    '❤️', '💔', '💯', '🔥', '🎉', '✨', '⭐', '🌟',
    '😴', '🤗', '😅', '😇', '🥰', '😋', '🤩', '😳',
    '🍺', '☕', '🎂', '🌹', '🐶', '🐱', '🌈', '☀️',
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
