import 'dart:convert';
import '../models/message_content.dart';

/// MessageContent ↔ JSON（落库到 messages.contentJson 列；也用于乐观消息重建）。
/// 字段命名与 store/types.ts 对齐，便于跨端排查。
class ContentJson {
  ContentJson._();

  static String encode(MessageContent c) => jsonEncode(toMap(c));

  static MessageContent decode(String raw) {
    try {
      return fromMap(jsonDecode(raw) as Map<String, dynamic>);
    } catch (_) {
      return const NotificationBody(eventType: 'message.corrupted');
    }
  }

  static Map<String, dynamic> toMap(MessageContent c) => switch (c) {
        TextBody(:final text, :final atUserIds) => {
            'kind': 'text',
            'text': text,
            'atUserIds': atUserIds,
          },
        ImageBody() => {
            'kind': 'image',
            'objectKey': c.objectKey,
            'thumbKey': c.thumbKey,
            'width': c.width,
            'height': c.height,
            'size': c.size,
            'mime': c.mime,
            'localPath': c.localPath,
          },
        VoiceBody() => {
            'kind': 'voice',
            'objectKey': c.objectKey,
            'durationMs': c.durationMs,
            'size': c.size,
            'codec': c.codec,
            'localPath': c.localPath,
          },
        FileBody() => {
            'kind': 'file',
            'objectKey': c.objectKey,
            'fileName': c.fileName,
            'size': c.size,
            'mime': c.mime,
          },
        VideoBody() => {
            'kind': 'video',
            'objectKey': c.objectKey,
            'fileName': c.fileName,
            'size': c.size,
            'mime': c.mime,
            'thumbKey': c.thumbKey,
            'durationMs': c.durationMs,
            'localPath': c.localPath,
          },
        NotificationBody(:final eventType, :final payload) => {
            'kind': 'notification',
            'eventType': eventType,
            'payload': payload,
          },
        CustomBody(:final customType, :final payload) => {
            'kind': 'custom',
            'customType': customType,
            'payload': payload,
          },
      };

  static MessageContent fromMap(Map<String, dynamic> j) {
    int? asInt(Object? v) => v == null ? null : (v as num).toInt();
    switch (j['kind'] as String?) {
      case 'text':
        return TextBody(
          (j['text'] ?? '') as String,
          atUserIds: ((j['atUserIds'] as List?) ?? const [])
              .map((e) => e.toString())
              .toList(),
        );
      case 'image':
        return ImageBody(
          objectKey: (j['objectKey'] ?? '') as String,
          thumbKey: j['thumbKey'] as String?,
          width: asInt(j['width']),
          height: asInt(j['height']),
          size: asInt(j['size']),
          mime: j['mime'] as String?,
          localPath: j['localPath'] as String?,
        );
      case 'voice':
        return VoiceBody(
          objectKey: (j['objectKey'] ?? '') as String,
          durationMs: asInt(j['durationMs']) ?? 0,
          size: asInt(j['size']),
          codec: j['codec'] as String?,
          localPath: j['localPath'] as String?,
        );
      case 'file':
        return FileBody(
          objectKey: (j['objectKey'] ?? '') as String,
          fileName: (j['fileName'] ?? '未命名文件') as String,
          size: asInt(j['size']),
          mime: j['mime'] as String?,
        );
      case 'video':
        return VideoBody(
          objectKey: (j['objectKey'] ?? '') as String,
          fileName: (j['fileName'] ?? '未命名视频') as String,
          size: asInt(j['size']),
          mime: j['mime'] as String?,
          thumbKey: j['thumbKey'] as String?,
          durationMs: asInt(j['durationMs']),
          localPath: j['localPath'] as String?,
        );
      case 'notification':
        return NotificationBody(
          eventType: (j['eventType'] ?? 'system') as String,
          payload: j['payload'] as String?,
        );
      case 'custom':
        return CustomBody(
          customType: (j['customType'] ?? 'custom') as String,
          payload: j['payload'] as String?,
        );
      default:
        return const NotificationBody(eventType: 'message.unsupported');
    }
  }
}
