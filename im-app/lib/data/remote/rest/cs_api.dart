import '../../models/cs_models.dart';
import 'api_client.dart';

/// 客服坐席工作台 REST（/api/v1/cs/conversations）。
class CsApi {
  CsApi(this._client);

  final ApiClient _client;

  Future<CsConversationList> listConversations({
    int limit = 50,
    int offset = 0,
  }) async {
    final resp = await _client.dio.get<dynamic>(
      '/api/v1/cs/conversations',
      queryParameters: {'limit': limit, 'offset': offset},
    );
    return CsConversationList.fromJson(resp.data as Map<String, dynamic>);
  }

  Future<CsConversation> getConversation(String convId) async {
    final resp = await _client.dio.get<dynamic>(
      '/api/v1/cs/conversations/${Uri.encodeComponent(convId)}',
    );
    return CsConversation.fromJson(resp.data as Map<String, dynamic>);
  }

  Future<void> claim(String convId) async {
    await _client.dio.post<dynamic>(
      '/api/v1/cs/conversations/${Uri.encodeComponent(convId)}/claim',
    );
  }

  Future<void> resolve(String convId) async {
    await _client.dio.post<dynamic>(
      '/api/v1/cs/conversations/${Uri.encodeComponent(convId)}/resolve',
    );
  }

  Future<List<CsInternalNote>> listNotes(String convId) async {
    final resp = await _client.dio.get<dynamic>(
      '/api/v1/cs/conversations/${Uri.encodeComponent(convId)}/notes',
    );
    final list =
        ((resp.data as Map<String, dynamic>)['notes'] as List?) ?? const [];
    return list
        .map((e) => CsInternalNote.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<CsInternalNote> createNote(String convId, String content) async {
    final resp = await _client.dio.post<dynamic>(
      '/api/v1/cs/conversations/${Uri.encodeComponent(convId)}/notes',
      data: {'content': content.trim()},
    );
    return CsInternalNote.fromJson(resp.data as Map<String, dynamic>);
  }
}
