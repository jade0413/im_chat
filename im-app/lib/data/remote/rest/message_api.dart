import 'api_client.dart';
import 'dto.dart';

/// 历史消息分页（协议 §5：GET /api/v1/convs/{id}/messages?end_seq=&limit=）。
/// 增量同步只回最新 N 条，更早缺口走这里懒加载（向上滚动翻页）。
class MessageApi {
  MessageApi(this._client);
  final ApiClient _client;

  Future<List<MessageItem>> history(
    String convId, {
    String? endSeq, // 拉取 seq < endSeq 的更早消息
    int limit = 20,
  }) async {
    final resp = await _client.dio.get<dynamic>(
      '/api/v1/convs/$convId/messages',
      queryParameters: {
        if (endSeq != null) 'end_seq': endSeq,
        'limit': limit,
      },
    );
    final data = resp.data;
    final list = (data is Map ? data['items'] ?? data['list'] : data) as List?;
    return (list ?? const [])
        .map((e) => MessageItem.fromJson(e as Map<String, dynamic>))
        .toList();
  }
}
