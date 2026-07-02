import '../../models/group_models.dart';
import 'api_client.dart';

/// 群聊 REST（im-server GroupController，/api/v1/groups）。
class GroupApi {
  GroupApi(this._client);

  final ApiClient _client;

  Future<GroupInfo> createGroup({
    required String name,
    required List<String> memberUserIds,
  }) async {
    final resp = await _client.dio.post<dynamic>(
      '/api/v1/groups',
      data: {
        'name': name.trim(),
        'memberUserIds': memberUserIds.map(_id).toList(),
      },
    );
    return GroupInfo.fromJson(resp.data as Map<String, dynamic>);
  }

  Future<GroupInfo> getGroup(String groupId) async {
    final resp = await _client.dio.get<dynamic>(
      '/api/v1/groups/${Uri.encodeComponent(groupId)}',
    );
    return GroupInfo.fromJson(resp.data as Map<String, dynamic>);
  }

  Future<List<GroupMember>> getMembers(String groupId) async {
    final resp = await _client.dio.get<dynamic>(
      '/api/v1/groups/${Uri.encodeComponent(groupId)}/members',
    );
    final list = (resp.data as List?) ?? const [];
    return list
        .map((e) => GroupMember.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<GroupMemberChange> addMembers(
    String groupId,
    List<String> userIds,
  ) async {
    final resp = await _client.dio.post<dynamic>(
      '/api/v1/groups/${Uri.encodeComponent(groupId)}/members',
      data: {'userIds': userIds.map(_id).toList()},
    );
    return GroupMemberChange.fromJson(resp.data as Map<String, dynamic>);
  }

  Future<GroupMemberChange> removeMember(String groupId, String userId) async {
    final resp = await _client.dio.delete<dynamic>(
      '/api/v1/groups/${Uri.encodeComponent(groupId)}/members/'
      '${Uri.encodeComponent(userId)}',
    );
    return GroupMemberChange.fromJson(resp.data as Map<String, dynamic>);
  }

  Future<GroupInfo> renameGroup(String groupId, String name) async {
    final resp = await _client.dio.patch<dynamic>(
      '/api/v1/groups/${Uri.encodeComponent(groupId)}',
      data: {'name': name.trim()},
    );
    return GroupInfo.fromJson(resp.data as Map<String, dynamic>);
  }

  Object _id(String value) => int.tryParse(value) ?? value;
}
