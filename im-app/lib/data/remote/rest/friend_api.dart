import '../../models/friend.dart';
import 'api_client.dart';

/// 好友 REST（im-server FriendController，/api/v1/friend/*）。
/// 注：响应已由 ApiClient 信封解包，这里直接拿 data。
class FriendApi {
  FriendApi(this._client);
  final ApiClient _client;

  /// 好友列表。
  Future<List<Friend>> listFriends() async {
    final resp = await _client.dio.get<dynamic>('/api/v1/friend/list');
    final list = (resp.data as List?) ?? const [];
    return list.map((e) => Friend.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// 发起好友申请（精确匹配 username 或完整手机号，D42）。
  Future<SendFriendRequestResult> sendRequest(
    String toUserId, {
    String note = '',
  }) async {
    final resp = await _client.dio.post<dynamic>('/api/v1/friend/requests',
        data: {'toUserId': int.tryParse(toUserId) ?? toUserId, 'note': note});
    return SendFriendRequestResult.fromJson(
      resp.data is Map<String, dynamic>
          ? resp.data as Map<String, dynamic>
          : null,
    );
  }

  /// 好友申请列表。role: incoming / outgoing。
  Future<List<FriendRequest>> listRequests({
    String role = 'incoming',
    int limit = 50,
  }) async {
    final resp = await _client.dio.get<dynamic>(
      '/api/v1/friend/requests',
      queryParameters: {'role': role, 'limit': limit},
    );
    final list = (resp.data as List?) ?? const [];
    return list
        .map((e) => FriendRequest.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<void> acceptRequest(String requestId) async {
    await _client.dio.post<dynamic>(
      '/api/v1/friend/requests/${Uri.encodeComponent(requestId)}/accept',
    );
  }

  Future<void> rejectRequest(String requestId) async {
    await _client.dio.post<dynamic>(
      '/api/v1/friend/requests/${Uri.encodeComponent(requestId)}/reject',
    );
  }

  Future<void> ignoreRequest(String requestId) async {
    await _client.dio.post<dynamic>(
      '/api/v1/friend/requests/${Uri.encodeComponent(requestId)}/ignore',
    );
  }

  /// 修改好友备注名。空字符串表示清空备注。
  Future<void> updateRemark(String friendId, String remark) async {
    await _client.dio.put<dynamic>(
      '/api/v1/friend/${Uri.encodeComponent(friendId)}/remark',
      data: {'remark': remark.trim()},
    );
  }

  /// 搜索用户（精确：username 或手机号）。
  Future<List<Friend>> searchUsers(String keyword) async {
    final resp = await _client.dio.get<dynamic>(
      '/api/v1/users/search',
      queryParameters: {'keyword': keyword},
    );
    final data = resp.data;
    final list =
        (data is List ? data : (data?['items'] ?? data?['list'])) as List?;
    return (list ?? const [])
        .map((e) => Friend.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// 批量查询用户公开资料，用于群成员昵称补全。
  Future<List<Friend>> batchUsers(List<String> ids) async {
    if (ids.isEmpty) return const [];
    final resp = await _client.dio.get<dynamic>(
      '/api/v1/users/batch',
      queryParameters: {'ids': ids.join(',')},
    );
    final list = (resp.data as List?) ?? const [];
    return list.map((e) => Friend.fromJson(e as Map<String, dynamic>)).toList();
  }
}
