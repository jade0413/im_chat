import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../core/logging.dart';
import '../data/im_engine.dart';
import '../data/local/app_database.dart';
import '../data/local/daos/conversation_dao.dart';
import '../data/local/daos/kv_dao.dart';
import '../data/local/daos/message_dao.dart';
import '../data/local/daos/outbox_dao.dart';
import '../data/local/daos/sync_cursor_dao.dart';
import '../data/models/chat_message.dart';
import '../data/models/conversation.dart';
import '../data/models/enums.dart';
import '../data/models/friend.dart';
import '../data/models/session_user.dart';
import '../data/remote/rest/api_client.dart';
import '../data/remote/rest/auth_api.dart';
import '../data/remote/rest/credential_storage.dart';
import '../data/remote/rest/conv_api.dart';
import '../data/remote/rest/file_api.dart';
import '../data/remote/rest/friend_api.dart';
import '../data/remote/rest/group_api.dart';
import '../data/remote/rest/message_api.dart';
import '../data/remote/rest/token_store.dart';
import '../data/attachment_sender.dart';
import '../data/repositories/conversation_repository.dart';
import '../data/repositories/message_repository.dart';

// ─── 基础设施 ─────────────────────────────────────────────

final credentialStorageProvider = Provider<CredentialStorage>((ref) {
  if (defaultTargetPlatform == TargetPlatform.macOS) {
    return const LocalCredentialStorage(fileName: 'macos_credentials.json');
  }

  return const SecureCredentialStorage(
    FlutterSecureStorage(
      mOptions: MacOsOptions(accountName: 'im_app_secure_storage'),
    ),
  );
});

final tokenStoreProvider = Provider<TokenStore>(
  (ref) => TokenStore(ref.watch(credentialStorageProvider)),
);

final databaseProvider = Provider<AppDatabase>((ref) {
  final db = AppDatabase();
  ref.onDispose(db.close);
  return db;
});

final conversationDaoProvider = Provider<ConversationDao>(
  (ref) => ref.watch(databaseProvider).conversationDao,
);
final messageDaoProvider = Provider<MessageDao>(
  (ref) => ref.watch(databaseProvider).messageDao,
);
final outboxDaoProvider = Provider<OutboxDao>(
  (ref) => ref.watch(databaseProvider).outboxDao,
);
final syncCursorDaoProvider = Provider<SyncCursorDao>(
  (ref) => ref.watch(databaseProvider).syncCursorDao,
);
final kvDaoProvider = Provider<KvDao>(
  (ref) => ref.watch(databaseProvider).kvDao,
);

// ─── REST ─────────────────────────────────────────────────

final apiClientProvider = Provider<ApiClient>((ref) {
  return ApiClient(
    ref.watch(tokenStoreProvider),
    onAuthExpired: () async => ref
        .read(authControllerProvider.notifier)
        .handleAuthExpired('登录已过期，请重新登录'),
  );
});

final authApiProvider =
    Provider<AuthApi>((ref) => AuthApi(ref.watch(apiClientProvider)));
final messageApiProvider =
    Provider<MessageApi>((ref) => MessageApi(ref.watch(apiClientProvider)));
final fileApiProvider =
    Provider<FileApi>((ref) => FileApi(ref.watch(apiClientProvider)));
final friendApiProvider =
    Provider<FriendApi>((ref) => FriendApi(ref.watch(apiClientProvider)));
final groupApiProvider =
    Provider<GroupApi>((ref) => GroupApi(ref.watch(apiClientProvider)));
final convApiProvider =
    Provider<ConvApi>((ref) => ConvApi(ref.watch(apiClientProvider)));

/// 好友列表（REST 拉取；下拉刷新用 ref.invalidate）。
final friendsProvider = FutureProvider<List<Friend>>(
  (ref) => ref.watch(friendApiProvider).listFriends(),
);

/// 好友申请列表。role: incoming / outgoing。
final friendRequestsProvider =
    FutureProvider.family<List<FriendRequest>, String>(
  (ref, role) => ref.watch(friendApiProvider).listRequests(role: role),
);

/// 群成员（含昵称/头像，供 @提及选择器）。按 groupId 拉成员 id 后批量补资料。
final groupMembersProvider =
    FutureProvider.family<List<Friend>, String>((ref, groupId) async {
  final members = await ref.watch(groupApiProvider).getMembers(groupId);
  final ids = members.map((m) => m.userId).toList();
  if (ids.isEmpty) return const [];
  return ref.watch(friendApiProvider).batchUsers(ids);
});

// ─── 消息引擎（连接层 + 本地缓存协调者）────────────────────

final imEngineProvider = Provider<ImEngine>((ref) {
  final tokens = ref.watch(tokenStoreProvider);
  final engine = ImEngine(
    database: ref.watch(databaseProvider),
    conversationDao: ref.watch(conversationDaoProvider),
    messageDao: ref.watch(messageDaoProvider),
    outboxDao: ref.watch(outboxDaoProvider),
    syncCursorDao: ref.watch(syncCursorDaoProvider),
    kvDao: ref.watch(kvDaoProvider),
    messageApi: ref.watch(messageApiProvider),
    apiClient: ref.watch(apiClientProvider),
    currentUser: () => ref.read(authControllerProvider).user,
    onAuthExpired: () =>
        ref.read(authControllerProvider.notifier).handleAuthExpired('账号已下线'),
    onKicked: (kick) => ref
        .read(authControllerProvider.notifier)
        .handleKicked(kick.message.isEmpty ? '当前账号已在其他设备登录' : kick.message),
    getDeviceId: tokens.getOrCreateDeviceId,
    getAccessToken: tokens.readAccessToken,
  );
  ref.onDispose(engine.dispose);
  return engine;
});

/// 连接状态（顶部网络横幅订阅）。
final connectionStateProvider = StreamProvider<ConnectionState>(
  (ref) => ref.watch(imEngineProvider).connectionState,
);

// ─── 仓储（UI 唯一读写入口；UI 不直接碰 Engine/DAO/WebSocket）────

final messageRepositoryProvider = Provider<MessageRepository>(
  (ref) => MessageRepository(
    ref.watch(imEngineProvider),
    ref.watch(messageDaoProvider),
  ),
);

final conversationRepositoryProvider = Provider<ConversationRepository>(
  (ref) => ConversationRepository(
    ref.watch(conversationDaoProvider),
    ref.watch(convApiProvider),
  ),
);

/// 富媒体发送编排（presign + 直传 + 发送）。
final attachmentSenderProvider = Provider<AttachmentSender>(
  (ref) => AttachmentSender(
    ref.watch(fileApiProvider),
    ref.watch(messageRepositoryProvider),
  ),
);

// ─── 响应式数据（经仓储订阅本地 DB）──────────────────────

final conversationsProvider = StreamProvider<List<Conversation>>(
  (ref) => ref.watch(conversationRepositoryProvider).watchAll(),
);

final conversationProvider =
    StreamProvider.family<Conversation?, String>((ref, convId) {
  return ref.watch(conversationRepositoryProvider).watch(convId);
});

final messagesProvider =
    StreamProvider.family<List<ChatMessage>, String>((ref, convId) {
  return ref.watch(messageRepositoryProvider).watch(convId);
});

// ─── 鉴权 / 会话态 ────────────────────────────────────────

class AuthState {
  const AuthState({
    this.user,
    this.bootstrapping = true,
    this.kickMessage,
    this.loginError,
  });

  final SessionUser? user;
  final bool bootstrapping; // 启动时恢复登录态中
  final String? kickMessage; // 被踢/下线提示
  final String? loginError;

  bool get isLoggedIn => user != null;

  AuthState copyWith({
    SessionUser? user,
    bool? bootstrapping,
    String? kickMessage,
    String? loginError,
    bool clearUser = false,
    bool clearKick = false,
  }) =>
      AuthState(
        user: clearUser ? null : (user ?? this.user),
        bootstrapping: bootstrapping ?? this.bootstrapping,
        kickMessage: clearKick ? null : (kickMessage ?? this.kickMessage),
        loginError: loginError,
      );
}

class AuthController extends Notifier<AuthState> {
  final _log = appLogger('auth');

  @override
  AuthState build() {
    Future.microtask(_bootstrap);
    return const AuthState();
  }

  TokenStore get _tokens => ref.read(tokenStoreProvider);
  ImEngine get _engine => ref.read(imEngineProvider);

  /// 冷启动：有 token 就恢复会话并连上网关；本地缓存让界面先离线渲染。
  Future<void> _bootstrap() async {
    final token = await _tokens.readAccessToken();
    if (token == null || token.isEmpty) {
      state = state.copyWith(bootstrapping: false);
      return;
    }
    final userId = await _tokens.readUserId();
    // 先用本地最小用户占位，避免离线启动卡白屏
    state = state.copyWith(
      user: SessionUser(id: userId ?? '0', tenantId: '1', account: ''),
      bootstrapping: false,
    );
    unawaited(_refreshProfile());
    unawaited(_engine.start());
  }

  Future<void> _refreshProfile() async {
    try {
      final me = await ref.read(authApiProvider).me();
      state = state.copyWith(user: me);
    } catch (e) {
      _log.fine('refresh profile failed (offline?): $e');
    }
  }

  /// 修改昵称：写服务端后刷新本地资料，使全局展示名即时更新。
  Future<bool> updateNickname(String nickname) async {
    final name = nickname.trim();
    if (name.isEmpty) return false;
    try {
      await ref.read(authApiProvider).updateProfile(nickname: name);
      await _refreshProfile();
      return true;
    } catch (e) {
      _log.warning('update nickname failed: $e');
      return false;
    }
  }

  Future<bool> login(String account, String password) async {
    state = state.copyWith(loginError: null);
    final previousUserId = state.user?.id ?? await _tokens.readUserId();
    try {
      await _engine.stop();
      final result = await ref
          .read(authApiProvider)
          .login(account: account, password: password);
      if (result.tokens.accessToken.isEmpty) {
        state = state.copyWith(loginError: '登录失败：服务端未返回有效凭证');
        return false;
      }
      await _tokens.saveTokens(
        accessToken: result.tokens.accessToken,
        refreshToken: result.tokens.refreshToken,
      );
      // 登录响应只含 token，真实资料（userId/昵称/头像）从 /users/me 拉
      SessionUser user = result.user;
      try {
        user = await ref.read(authApiProvider).me();
      } catch (e) {
        _log.warning('fetch profile after login failed: $e');
      }
      await _wipeLocalIfAccountChanged(previousUserId, user.id);
      await _tokens.saveUserId(user.id);
      state = AuthState(user: user, bootstrapping: false);
      await _engine.start();
      return true;
    } catch (e) {
      _log.warning('login failed: $e');
      state = state.copyWith(loginError: _loginErrorText(e));
      return false;
    }
  }

  String _loginErrorText(Object e) {
    final inner = e is DioException ? e.error : e;
    if (inner is ApiException) return inner.message;
    return '登录失败，请检查账号/密码或网络';
  }

  /// 账号注册（account 可为用户名/手机号/邮箱）。成功后自动登录。
  Future<bool> register(String account, String password,
      {String? nickname}) async {
    state = state.copyWith(loginError: null);
    final previousUserId = state.user?.id ?? await _tokens.readUserId();
    try {
      await _engine.stop();
      final result = await ref.read(authApiProvider).register(
            account: account,
            password: password,
            nickname: nickname,
          );
      if (result.tokens.accessToken.isEmpty) {
        state = state.copyWith(loginError: '注册失败：服务端未返回有效凭证');
        return false;
      }
      await _tokens.saveTokens(
        accessToken: result.tokens.accessToken,
        refreshToken: result.tokens.refreshToken,
      );
      SessionUser user = result.user;
      try {
        user = await ref.read(authApiProvider).me();
      } catch (_) {}
      await _wipeLocalIfAccountChanged(previousUserId, user.id);
      await _tokens.saveUserId(user.id);
      state = AuthState(user: user, bootstrapping: false);
      await _engine.start();
      return true;
    } catch (e) {
      _log.warning('register failed: $e');
      state = state.copyWith(loginError: _loginErrorText(e));
      return false;
    }
  }

  Future<void> logout() async {
    await _engine.stop();
    await _tokens.clear();
    await _wipeLocal();
    state = const AuthState(bootstrapping: false);
  }

  Future<void> handleAuthExpired(String message) async {
    await _engine.stop();
    await _tokens.clear();
    await _wipeLocal();
    state =
        const AuthState(bootstrapping: false).copyWith(kickMessage: message);
  }

  Future<void> handleKicked(String message) async {
    await _engine.stop();
    await _tokens.clear();
    await _wipeLocal();
    state =
        const AuthState(bootstrapping: false).copyWith(kickMessage: message);
  }

  void clearKickMessage() => state = state.copyWith(clearKick: true);

  Future<void> _wipeLocalIfAccountChanged(
    String? previousUserId,
    String nextUserId,
  ) async {
    if (previousUserId == nextUserId) return;
    await _wipeLocal();
  }

  Future<void> _wipeLocal() async {
    final db = ref.read(databaseProvider);
    await db.transaction(() async {
      await db.customStatement('DELETE FROM outbox_messages');
      await db.customStatement('DELETE FROM message_attachments');
      await db.customStatement('DELETE FROM messages');
      await db.customStatement('DELETE FROM conversations');
      await db.customStatement('DELETE FROM sync_cursors');
      await db.customStatement('DELETE FROM app_kv');
      await db.customStatement('DELETE FROM groups');
      await db.customStatement('DELETE FROM users');
    });
  }
}

final authControllerProvider =
    NotifierProvider<AuthController, AuthState>(AuthController.new);
