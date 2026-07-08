import 'api_client.dart';

class PresenceApi {
  PresenceApi(this._client);

  final ApiClient _client;

  Future<bool> isOnline(String userId) async {
    final resp = await _client.dio.get<Map<String, dynamic>>(
      '/api/v1/presence/users/${int.tryParse(userId) ?? userId}',
    );
    return resp.data?['online'] == true;
  }
}
