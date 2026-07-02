import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/auth/login_page.dart';
import '../features/chat/chat_page.dart';
import '../features/contacts/add_friend_page.dart';
import '../features/groups/create_group_page.dart';
import '../features/home/home_shell.dart';
import '../features/search/message_search_page.dart';
import '../features/splash/splash_page.dart';
import 'providers.dart';

/// 路由 + 鉴权重定向。authController 状态变化时刷新重定向。
final routerProvider = Provider<GoRouter>((ref) {
  final refresh = _AuthRefresh(ref);
  ref.onDispose(refresh.dispose);

  return GoRouter(
    initialLocation: '/splash',
    refreshListenable: refresh,
    redirect: (context, state) {
      final auth = ref.read(authControllerProvider);
      final loc = state.matchedLocation;
      if (auth.bootstrapping) {
        return loc == '/splash' ? null : '/splash';
      }
      final loggedIn = auth.isLoggedIn;
      final atAuth = loc == '/login' || loc == '/splash';
      if (!loggedIn) {
        return atAuth ? (loc == '/splash' ? '/login' : null) : '/login';
      }
      if (atAuth) return '/';
      return null;
    },
    routes: [
      GoRoute(path: '/splash', builder: (_, __) => const SplashPage()),
      GoRoute(path: '/login', builder: (_, __) => const LoginPage()),
      GoRoute(path: '/', builder: (_, __) => const HomeShell()),
      GoRoute(path: '/search', builder: (_, __) => const MessageSearchPage()),
      GoRoute(path: '/add-friend', builder: (_, __) => const AddFriendPage()),
      GoRoute(
        path: '/create-group',
        builder: (_, __) => const CreateGroupPage(),
      ),
      GoRoute(
        path: '/chat/:convId',
        builder: (_, s) => ChatPage(convId: s.pathParameters['convId']!),
      ),
    ],
  );
});

/// 把 Riverpod 的 authController 变化桥接成 Listenable 供 GoRouter 刷新。
class _AuthRefresh extends ChangeNotifier {
  _AuthRefresh(Ref ref) {
    _sub = ref.listen(
      authControllerProvider,
      (_, __) => notifyListeners(),
      fireImmediately: false,
    );
  }
  late final ProviderSubscription _sub;

  @override
  void dispose() {
    _sub.close();
    super.dispose();
  }
}
