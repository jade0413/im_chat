import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/providers.dart';
import '../../core/theme/lumo_colors.dart';
import '../../shared/widgets/connection_banner.dart';
import '../../shared/widgets/lumo_avatar.dart';
import '../chat/chat_page.dart';
import '../contacts/contacts_page.dart';
import '../conversations/conversation_list.dart';
import '../profile/profile_page.dart';

/// 桌面三栏选中的会话（移动端走路由 push，不用此 provider）。
final selectedConvProvider = StateProvider<String?>((ref) => null);

/// 自适应主框架：宽屏（桌面/平板横屏）三栏，窄屏（手机）底部 Tab。
class HomeShell extends StatelessWidget {
  const HomeShell({super.key});

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, c) =>
          c.maxWidth >= 900 ? const _DesktopHome() : const _MobileHome(),
    );
  }
}

// ─── 移动端：底部 Tab ──────────────────────────────────────
class _MobileHome extends StatefulWidget {
  const _MobileHome();
  @override
  State<_MobileHome> createState() => _MobileHomeState();
}

class _MobileHomeState extends State<_MobileHome> {
  int _index = 0;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            const ConnectionBanner(),
            Expanded(
              child: IndexedStack(
                index: _index,
                children: [
                  ConversationList(
                    onOpen: (conv) => context.push('/chat/${conv.convId}'),
                  ),
                  const ContactsPage(),
                  const ProfilePage(),
                ],
              ),
            ),
          ],
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (i) => setState(() => _index = i),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.chat_bubble_outline),
            selectedIcon: Icon(Icons.chat_bubble),
            label: '消息',
          ),
          NavigationDestination(
            icon: Icon(Icons.people_outline),
            selectedIcon: Icon(Icons.people),
            label: '通讯录',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person),
            label: '我',
          ),
        ],
      ),
    );
  }
}

// ─── 桌面端：三栏 ──────────────────────────────────────────
class _DesktopHome extends ConsumerStatefulWidget {
  const _DesktopHome();
  @override
  ConsumerState<_DesktopHome> createState() => _DesktopHomeState();
}

class _DesktopHomeState extends ConsumerState<_DesktopHome> {
  int _nav = 0;

  @override
  Widget build(BuildContext context) {
    final selected = ref.watch(selectedConvProvider);
    final user = ref.watch(authControllerProvider).user;
    return Scaffold(
      body: Row(
        children: [
          // 左：导航栏
          Container(
            width: 72,
            color: Theme.of(context).appBarTheme.backgroundColor,
            child: Column(
              children: [
                const SizedBox(height: 16),
                _navIcon(Icons.chat_bubble_rounded, 0),
                _navIcon(Icons.people_alt_rounded, 1),
                _navIcon(Icons.folder_rounded, 2),
                const Spacer(),
                Padding(
                  padding: const EdgeInsets.only(bottom: 16),
                  child: LumoAvatar(
                    name: user?.displayName ?? '我',
                    url: user?.avatar,
                    size: 36,
                  ),
                ),
              ],
            ),
          ),
          const VerticalDivider(width: 1),
          // 中：列表
          SizedBox(
            width: 320,
            child: Column(
              children: [
                const ConnectionBanner(),
                Expanded(child: _midPanel()),
              ],
            ),
          ),
          const VerticalDivider(width: 1),
          // 右：聊天 / 详情
          Expanded(
            child: _nav == 0
                ? (selected == null
                    ? const _EmptyChat()
                    : ChatPage(convId: selected, embedded: true))
                : (_nav == 1
                    ? ContactsPage(onOpenConversation: _selectConversation)
                    : const ProfilePage()),
          ),
        ],
      ),
    );
  }

  Widget _midPanel() {
    if (_nav == 1) {
      return ContactsPage(onOpenConversation: _selectConversation);
    }
    if (_nav == 2) return const ProfilePage();
    return ConversationList(
      selectedConvId: ref.watch(selectedConvProvider),
      onOpen: (conv) =>
          ref.read(selectedConvProvider.notifier).state = conv.convId,
    );
  }

  void _selectConversation(String convId) {
    ref.read(selectedConvProvider.notifier).state = convId;
    setState(() => _nav = 0);
  }

  Widget _navIcon(IconData icon, int i) {
    final active = _nav == i;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Material(
        color: active ? LumoColors.primarySoft : Colors.transparent,
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: () => setState(() => _nav = i),
          child: Padding(
            padding: const EdgeInsets.all(10),
            child: Icon(
              icon,
              color: active ? LumoColors.primary : LumoColors.textSecondary,
            ),
          ),
        ),
      ),
    );
  }
}

class _EmptyChat extends StatelessWidget {
  const _EmptyChat();
  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.forum_outlined, size: 56, color: LumoColors.textSecondary),
          SizedBox(height: 12),
          Text(
            '选择一个会话开始聊天',
            style: TextStyle(color: LumoColors.textSecondary),
          ),
        ],
      ),
    );
  }
}
