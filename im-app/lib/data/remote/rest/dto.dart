import '../../models/session_user.dart';

/// 登录/注册返回的 token 对。字段名按 im-server 约定，解析保持宽容。
class TokenBundle {
  const TokenBundle({required this.accessToken, required this.refreshToken});
  final String accessToken;
  final String refreshToken;

  factory TokenBundle.fromJson(Map<String, dynamic> j) => TokenBundle(
        accessToken: (j['accessToken'] ?? j['access_token'] ?? '') as String,
        refreshToken: (j['refreshToken'] ?? j['refresh_token'] ?? '') as String,
      );
}

/// 登录结果：token + 用户资料。
class LoginResult {
  const LoginResult({required this.tokens, required this.user});
  final TokenBundle tokens;
  final SessionUser user;

  factory LoginResult.fromJson(Map<String, dynamic> j) => LoginResult(
        tokens: TokenBundle.fromJson(j),
        user: SessionUser.fromJson(
          (j['user'] ?? j['profile'] ?? j) as Map<String, dynamic>,
        ),
      );
}

/// 历史消息分页项（REST，字段对齐 im-web api/types MessageItemResponse）。
class MessageItem {
  const MessageItem({
    required this.convId,
    required this.seq,
    required this.senderId,
    required this.msgType,
    required this.status,
    required this.sendTime,
    this.serverMsgId,
    this.clientMsgId,
    this.text,
    this.objectKey,
    this.thumbKey,
    this.fileName,
    this.mime,
    this.size,
    this.durationMs,
    this.width,
    this.height,
    this.codec,
  });

  final String convId;
  final String seq;
  final String senderId;
  final int msgType; // 1 text / 2 image / 3 voice / 4 file/video
  final int status; // 1 normal / 2 revoked
  final String sendTime;
  final String? serverMsgId;
  final String? clientMsgId;
  final String? text;
  final String? objectKey;
  final String? thumbKey;
  final String? fileName;
  final String? mime;
  final int? size;
  final int? durationMs;
  final int? width;
  final int? height;
  final String? codec;

  factory MessageItem.fromJson(Map<String, dynamic> j) {
    String s(Object? v) => (v ?? '').toString();
    int? i(Object? v) => v == null ? null : (v as num).toInt();
    return MessageItem(
      convId: s(j['convId']),
      seq: s(j['seq']),
      senderId: s(j['senderId']),
      msgType: i(j['msgType']) ?? 1,
      status: i(j['status']) ?? 1,
      sendTime: s(j['sendTime']),
      serverMsgId: j['serverMsgId']?.toString(),
      clientMsgId: j['clientMsgId'] as String?,
      text: j['text'] as String?,
      objectKey: j['objectKey'] as String?,
      thumbKey: j['thumbKey'] as String?,
      fileName: j['fileName'] as String?,
      mime: j['mime'] as String?,
      size: i(j['size']),
      durationMs: i(j['durationMs']),
      width: i(j['width']),
      height: i(j['height']),
      codec: j['codec'] as String?,
    );
  }
}

/// 文件直传预签名（D10：客户端走 MinIO 预签名 URL 直传）。
class PresignResult {
  const PresignResult({
    required this.uploadUrl,
    required this.objectKey,
    this.headers = const {},
    this.instant = false,
  });
  final String uploadUrl;
  final String objectKey;
  final Map<String, String> headers;
  final bool instant;

  factory PresignResult.fromJson(Map<String, dynamic> j) {
    final rawHeaders = (j['requiredHeaders'] ?? j['headers']) as Map?;
    return PresignResult(
      uploadUrl: (j['uploadUrl'] ?? j['url'] ?? '') as String,
      objectKey: (j['objectKey'] ?? j['key'] ?? '') as String,
      instant: j['instant'] == true,
      headers: rawHeaders?.map(
            (k, v) => MapEntry(k.toString(), v.toString()),
          ) ??
          const {},
    );
  }
}
