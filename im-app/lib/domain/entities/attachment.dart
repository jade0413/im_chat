/// 富媒体附件实体。图片、语音、文件、视频共享这套结构。
class Attachment {
  const Attachment({
    required this.attachmentId,
    required this.clientMsgId,
    required this.convId,
    required this.kind,
    this.objectKey,
    this.thumbKey,
    this.localPath,
    this.fileName,
    this.mime,
    this.size,
    this.width,
    this.height,
    this.durationMs,
    this.status = 'local',
  });

  final String attachmentId;
  final String clientMsgId;
  final String convId;
  final String kind;
  final String? objectKey;
  final String? thumbKey;
  final String? localPath;
  final String? fileName;
  final String? mime;
  final int? size;
  final int? width;
  final int? height;
  final int? durationMs;
  final String status;
}
