import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';
import '../../app/update_providers.dart';
import '../../core/platform/platform_info.dart';
import '../../core/theme/lumo_colors.dart';
import '../../core/update/update_service.dart';
import '../../shared/widgets/lumo_avatar.dart';

/// 「我」页：资料 + 平台信息 + 热更新检查 + 退出登录。
class ProfilePage extends ConsumerWidget {
  const ProfilePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authControllerProvider).user;
    final update = ref.watch(startupUpdateCheckProvider);
    return SafeArea(
      bottom: false,
      child: ListView(
        children: [
          const SizedBox(height: 12),
          InkWell(
            onTap: user == null
                ? null
                : () => _editNickname(context, ref, user.nickname),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  LumoAvatar(
                    name: user?.displayName ?? '我',
                    url: user?.avatar,
                    size: 60,
                    radius: 16,
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          user?.displayName ?? '未登录',
                          style: const TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          user?.username != null
                              ? '@${user!.username}'
                              : (user?.account ?? ''),
                          style:
                              const TextStyle(color: LumoColors.textSecondary),
                        ),
                      ],
                    ),
                  ),
                  if (user != null)
                    const Icon(Icons.edit_outlined,
                        size: 18, color: LumoColors.textSecondary),
                ],
              ),
            ),
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.devices),
            title: const Text('当前平台'),
            trailing: Text(
              PlatformInfo.label,
              style: const TextStyle(color: LumoColors.textSecondary),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.system_update),
            title: const Text('热更新'),
            subtitle: Text(_updateLabel(update.valueOrNull)),
            trailing: update.isLoading
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.chevron_right),
            onTap: () => ref.invalidate(startupUpdateCheckProvider),
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.logout, color: LumoColors.danger),
            title: const Text(
              '退出登录',
              style: TextStyle(color: LumoColors.danger),
            ),
            onTap: () => _confirmLogout(context, ref),
          ),
        ],
      ),
    );
  }

  String _updateLabel(UpdateResult? r) => switch (r) {
        UpToDate() => '已是最新版本',
        PatchReady() => '补丁已就绪，重启生效',
        DesktopUpdateAvailable(:final version) => '发现新版本 $version',
        UpdateUnavailable() => '点击检查更新',
        _ => '检查中…',
      };

  /// 修改自己的昵称：弹窗输入 → 写服务端 → 刷新本地资料。
  Future<void> _editNickname(
      BuildContext context, WidgetRef ref, String? current) async {
    final controller = TextEditingController(text: current ?? '');
    final messenger = ScaffoldMessenger.of(context);
    final saved = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('修改昵称'),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLength: 32,
          decoration: const InputDecoration(
            hintText: '输入昵称',
            counterText: '',
          ),
          onSubmitted: (_) => Navigator.pop(ctx, true),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('保存'),
          ),
        ],
      ),
    );
    if (saved != true) return;
    final name = controller.text.trim();
    if (name.isEmpty) {
      messenger.showSnackBar(const SnackBar(content: Text('昵称不能为空')));
      return;
    }
    final ok =
        await ref.read(authControllerProvider.notifier).updateNickname(name);
    messenger.showSnackBar(
      SnackBar(content: Text(ok ? '昵称已更新' : '昵称修改失败，请重试')),
    );
  }

  Future<void> _confirmLogout(BuildContext context, WidgetRef ref) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('退出登录'),
        content: const Text('确定退出当前账号？'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('取消')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('退出')),
        ],
      ),
    );
    if (ok == true) await ref.read(authControllerProvider.notifier).logout();
  }
}
