import '../../models/conversation.dart';
import 'api_client.dart';

/// 会话 REST（im-server ConversationController）。
class ConvApi {
  ConvApi(this._client);
  final ApiClient _client;

  /// 打开或创建与某用户的单聊（D17 开放式单聊，幂等）。
  /// POST /api/v1/convs/c2c?toUserId=
  Future<Conversation> openC2c(String toUserId) async {
    final resp = await _client.dio.post<dynamic>(
      '/api/v1/convs/c2c',
      queryParameters: {'toUserId': int.tryParse(toUserId) ?? toUserId},
    );
    return _toConversation(resp.data as Map<String, dynamic>);
  }

  Conversation _toConversation(Map<String, dynamic> j) {
    String s(Object? v) => (v ?? '').toString();
    return Conversation(
      convId: s(j['convId']),
      type: (j['type'] as num?)?.toInt() ?? 1,
      title: (j['title'] as String?)?.isNotEmpty == true
          ? j['title'] as String
          : '会话',
      avatar: j['avatar'] as String?,
      peerUserId: j['peerUserId'] == null ? null : s(j['peerUserId']),
      groupId: j['groupId'] == null ? null : s(j['groupId']),
      maxSeq: s(j['maxSeq']).isEmpty ? '0' : s(j['maxSeq']),
      readSeq: s(j['readSeq']).isEmpty ? '0' : s(j['readSeq']),
    );
  }
}
