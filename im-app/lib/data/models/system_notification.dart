import 'dart:convert';

import 'message_content.dart';

String systemNotificationText(NotificationBody content) {
  switch (content.eventType) {
    case 'message.revoked':
      return '一条消息已撤回';
    case 'message.unsupported':
      return '收到一条暂不支持的消息';
    case 'message.empty':
      return '收到一条空消息';
    case 'message.corrupted':
      return '收到一条异常消息';
    case 'group.created':
      return _withPayload(
        content.payload,
        (p) => '${_str(p, ['name'], fallback: '群组')} 已创建',
      );
    case 'group.member_added':
      return _withPayload(content.payload, (p) {
        final ids = _list(p, ['user_ids', 'userIds', 'changedUserIds']);
        if (ids.length <= 1) return '新成员加入了群组';
        return '${ids.length} 位新成员加入了群组';
      });
    case 'group.member_removed':
      return '有成员已退出群组';
    case 'group.name_changed':
      return _withPayload(content.payload, (p) {
        final name = _str(p, ['new', 'newName', 'name']);
        return name.isEmpty ? '群名称已更新' : '群名称已改为「$name」';
      });
    case 'friend.request':
      return _withPayload(content.payload, (p) {
        final name = _str(p, ['from_nickname', 'fromNickname'], fallback: '有人');
        final note = _str(p, ['note']);
        return note.isEmpty ? '$name 请求加你为好友' : '$name 请求加你为好友：$note';
      });
    case 'friend.accepted':
      return _withPayload(
        content.payload,
        (p) => '${_str(p, [
              'to_nickname',
              'toNickname'
            ], fallback: '对方')} 已通过你的好友申请',
      );
    case 'friend.added':
      return _withPayload(
        content.payload,
        (p) => '${_str(p, [
              'from_nickname',
              'fromNickname'
            ], fallback: '有人')} 已添加你为好友',
      );
    case 'cs.pending':
      return _withPayload(content.payload, (p) {
        final count = _int(p, ['count']);
        return count > 0 ? '你有 $count 个待接待会话' : '你有待接待会话';
      });
    case 'cs.assigned':
      return '客服已接入';
    case 'cs.resolved':
      return '会话已结束';
    default:
      return '系统通知';
  }
}

String _withPayload(String? payload, String Function(Map<String, dynamic>) f) {
  if (payload == null || payload.trim().isEmpty) return '系统通知';
  try {
    final decoded = jsonDecode(payload);
    if (decoded is Map<String, dynamic>) return f(decoded);
    if (decoded is Map) {
      return f(decoded.map((key, value) => MapEntry(key.toString(), value)));
    }
  } catch (_) {
    return '系统通知';
  }
  return '系统通知';
}

String _str(
  Map<String, dynamic> map,
  List<String> keys, {
  String fallback = '',
}) {
  for (final key in keys) {
    final value = map[key];
    if (value != null && value.toString().trim().isNotEmpty) {
      return value.toString().trim();
    }
  }
  return fallback;
}

int _int(Map<String, dynamic> map, List<String> keys) {
  for (final key in keys) {
    final value = map[key];
    if (value is num) return value.toInt();
    if (value != null) return int.tryParse(value.toString()) ?? 0;
  }
  return 0;
}

List<Object?> _list(Map<String, dynamic> map, List<String> keys) {
  for (final key in keys) {
    final value = map[key];
    if (value is List) return value;
  }
  return const [];
}
