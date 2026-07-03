import 'dart:convert';

import 'enums.dart';

/// 消息内容（移植 im-web MessageContent 联合类型）。
/// Dart 3 sealed class——switch 时编译器强制穷尽所有分支，富媒体扩展安全。
sealed class MessageContent {
  const MessageContent();

  ContentKind get kind;

  /// 会话列表 / 通知摘要文案。
  String get abstract => switch (this) {
        TextBody(:final text) =>
          text.length > 40 ? text.substring(0, 40) : text,
        ImageBody() => '[图片]',
        VoiceBody() => '[语音]',
        FileBody(:final fileName) => '[文件] $fileName',
        VideoBody() => '[视频]',
        NotificationBody() => '[系统消息]',
        CustomBody() => _customAbstract(this as CustomBody),
      };
}

class TextBody extends MessageContent {
  const TextBody(this.text, {this.atUserIds = const []});
  final String text;
  final List<String> atUserIds; // @提及（@全员=-1）
  @override
  ContentKind get kind => ContentKind.text;
}

class ImageBody extends MessageContent {
  const ImageBody({
    required this.objectKey,
    this.thumbKey,
    this.width,
    this.height,
    this.size,
    this.mime,
    this.localPath, // 乐观发送：上传前的本地预览路径
  });
  final String objectKey;
  final String? thumbKey;
  final int? width;
  final int? height;
  final int? size;
  final String? mime;
  final String? localPath;
  @override
  ContentKind get kind => ContentKind.image;
}

class VoiceBody extends MessageContent {
  const VoiceBody({
    required this.objectKey,
    required this.durationMs,
    this.size,
    this.codec,
    this.localPath,
  });
  final String objectKey;
  final int durationMs;
  final int? size;
  final String? codec;
  final String? localPath;
  @override
  ContentKind get kind => ContentKind.voice;
}

class FileBody extends MessageContent {
  const FileBody({
    required this.objectKey,
    required this.fileName,
    this.size,
    this.mime,
  });
  final String objectKey;
  final String fileName;
  final int? size;
  final String? mime;
  @override
  ContentKind get kind => ContentKind.file;
}

class VideoBody extends MessageContent {
  const VideoBody({
    required this.objectKey,
    required this.fileName,
    this.size,
    this.mime,
    this.thumbKey,
    this.durationMs,
    this.localPath,
  });
  final String objectKey;
  final String fileName;
  final int? size;
  final String? mime;
  final String? thumbKey;
  final int? durationMs;
  final String? localPath;
  @override
  ContentKind get kind => ContentKind.video;
}

/// 系统事件灰条（建群/进群/坐席分配/撤回提示...），按 eventType 渲染。
class NotificationBody extends MessageContent {
  const NotificationBody({required this.eventType, this.payload});
  final String eventType;
  final String? payload;
  @override
  ContentKind get kind => ContentKind.notification;
}

class CustomBody extends MessageContent {
  const CustomBody({required this.customType, this.payload});
  final String customType;
  final String? payload;
  @override
  ContentKind get kind => ContentKind.custom;
}

String _customAbstract(CustomBody body) {
  final raw = body.payload;
  if (raw == null || raw.isEmpty) return '[${body.customType}]';
  try {
    final decoded = jsonDecode(raw);
    if (decoded is! Map) return '[${body.customType}]';
    if (body.customType == 'quote.reply') {
      final text = (decoded['text'] ?? '').toString();
      return text.isEmpty ? '[引用回复]' : text;
    }
    if (body.customType == 'merge.forward') {
      final title = (decoded['title'] ?? '合并转发').toString();
      return '[合并转发] $title';
    }
  } catch (_) {
    return '[${body.customType}]';
  }
  return '[${body.customType}]';
}
