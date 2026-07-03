import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';
import '../../app/update_providers.dart';
import '../../core/platform/platform_info.dart';
import '../../core/theme/lumo_colors.dart';
import '../../core/update/update_service.dart';
import '../../data/file_hash.dart';
import '../../data/remote/rest/api_client.dart';
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
          Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                GestureDetector(
                  onTap:
                      user == null ? null : () => _changeAvatar(context, ref),
                  child: Stack(
                    children: [
                      LumoAvatar(
                        name: user?.displayName ?? '我',
                        url: user?.avatar,
                        size: 60,
                        radius: 16,
                      ),
                      if (user != null)
                        Positioned(
                          right: 4,
                          bottom: 4,
                          child: Container(
                            width: 22,
                            height: 22,
                            decoration: BoxDecoration(
                              color: Theme.of(context).colorScheme.primary,
                              borderRadius: BorderRadius.circular(11),
                              border: Border.all(color: Colors.white, width: 2),
                            ),
                            child: const Icon(
                              Icons.camera_alt,
                              color: Colors.white,
                              size: 13,
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: InkWell(
                    borderRadius: BorderRadius.circular(8),
                    onTap: user == null
                        ? null
                        : () => _editNickname(context, ref, user.nickname),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 6),
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
                            _usernameText(user?.username, user?.account),
                            style: const TextStyle(
                                color: LumoColors.textSecondary),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                if (user != null)
                  IconButton(
                    tooltip: '修改昵称',
                    onPressed: () => _editNickname(context, ref, user.nickname),
                    icon: const Icon(Icons.edit_outlined, size: 18),
                  ),
              ],
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
            leading: const Icon(Icons.alternate_email_rounded),
            title: const Text('微光号'),
            subtitle: Text(_usernameText(
              user?.username,
              '设置后可通过微光号添加好友',
            )),
            trailing: const Icon(Icons.chevron_right),
            onTap: user == null ? null : () => _editUsername(context, ref),
          ),
          SwitchListTile(
            secondary: const Icon(Icons.verified_user_outlined),
            title: const Text('加我为好友时需要验证'),
            subtitle: const Text('关闭后，对方发送申请会自动通过'),
            value: (user?.friendVerifyRequired ?? 1) == 1,
            onChanged: user == null
                ? null
                : (value) => _updateFriendVerify(context, ref, value),
          ),
          if (user?.isAgent ?? false)
            ListTile(
              leading: const Icon(Icons.support_agent_rounded),
              title: const Text('坐席状态'),
              subtitle: Text(_agentStatusLabel(user?.agentStatus ?? 0)),
              trailing: const Icon(Icons.chevron_right),
              onTap: user == null
                  ? null
                  : () => _editAgentStatus(context, ref, user.agentStatus),
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
          ListTile(
            leading: const Icon(Icons.cleaning_services_outlined),
            title: const Text('清理媒体缓存'),
            subtitle: const Text('自动保留 30 天 / 最多 512MB，可手动清空'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _clearMediaCache(context, ref),
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

  String _usernameText(String? username, String? fallback) {
    if (username != null && username.isNotEmpty) return '@$username';
    return fallback ?? '';
  }

  String _updateLabel(UpdateResult? r) => switch (r) {
        UpToDate() => '已是最新版本',
        PatchReady() => '补丁已就绪，重启生效',
        DesktopUpdateAvailable(:final version) => '发现新版本 $version',
        UpdateUnavailable() => '点击检查更新',
        _ => '检查中…',
      };

  Future<void> _changeAvatar(BuildContext context, WidgetRef ref) async {
    final messenger = ScaffoldMessenger.of(context);
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.image,
        withData: true,
      );
      final files = result?.files ?? const <PlatformFile>[];
      if (files.isEmpty) return;
      final file = files.first;
      final bytes = file.bytes ??
          (file.path == null ? null : await File(file.path!).readAsBytes());
      if (bytes == null || bytes.isEmpty) {
        messenger.showSnackBar(const SnackBar(content: Text('无法读取头像图片')));
        return;
      }
      final mime = _avatarMimeFromName(file.name);
      final oldAvatar = ref.read(authControllerProvider).user?.avatar;
      final fileApi = ref.read(fileApiProvider);
      final sha256 = await sha256Hex(bytes);
      final presign = await fileApi.presign(
        fileName: file.name,
        mime: mime,
        size: bytes.length,
        sha256: sha256,
      );
      if (!presign.instant) {
        await fileApi.uploadDirect(presign, bytes);
        await fileApi.confirm(
          objectKey: presign.objectKey,
          size: bytes.length,
          mime: mime,
        );
      }
      final ok = await ref
          .read(authControllerProvider.notifier)
          .updateAvatar(presign.objectKey);
      if (ok) {
        await _evictOldAvatarCache(ref, oldAvatar, presign.objectKey);
      }
      final message =
          ok ? (presign.instant ? '头像已更新（秒传）' : '头像已更新') : '头像保存失败，请重试';
      messenger.showSnackBar(
        SnackBar(
          content: Text(message),
        ),
      );
    } catch (e) {
      messenger.showSnackBar(
        SnackBar(content: Text('头像上传失败：${describeApiError(e)}')),
      );
    }
  }

  String _avatarMimeFromName(String name) {
    final ext = name.contains('.') ? name.split('.').last.toLowerCase() : '';
    return switch (ext) {
      'jpg' || 'jpeg' => 'image/jpeg',
      'png' => 'image/png',
      'gif' => 'image/gif',
      'webp' => 'image/webp',
      'heic' => 'image/heic',
      'heif' => 'image/heif',
      _ => 'image/jpeg',
    };
  }

  Future<void> _evictOldAvatarCache(
    WidgetRef ref,
    String? oldAvatar,
    String newAvatar,
  ) async {
    final avatar = oldAvatar?.trim();
    if (avatar == null ||
        avatar.isEmpty ||
        avatar == newAvatar ||
        _isHttpUrl(avatar)) {
      return;
    }
    ref.invalidate(fileCacheProvider(avatar));
    await ref.read(mediaCacheServiceProvider).evictObjectKeys([avatar]);
  }

  bool _isHttpUrl(String value) {
    final uri = Uri.tryParse(value);
    return uri != null && (uri.scheme == 'http' || uri.scheme == 'https');
  }

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

  Future<void> _clearMediaCache(BuildContext context, WidgetRef ref) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('清理媒体缓存'),
        content:
            const Text('只会删除本机已缓存的媒体文件，不会删除聊天记录或服务端文件。之后打开图片、语音、视频、文件时会重新下载。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('清理'),
          ),
        ],
      ),
    );
    if (ok != true || !context.mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    try {
      final result = await ref.read(mediaCacheServiceProvider).clearAll();
      if (!context.mounted) return;
      final urlSuffix = result.clearedDownloadUrlEntries > 0
          ? '，刷新 ${result.clearedDownloadUrlEntries} 条下载地址'
          : '';
      messenger.showSnackBar(
        SnackBar(content: Text('已清理媒体缓存 ${result.displaySize}$urlSuffix')),
      );
    } catch (e) {
      if (!context.mounted) return;
      messenger.showSnackBar(SnackBar(content: Text('清理失败：$e')));
    }
  }

  Future<void> _editUsername(BuildContext context, WidgetRef ref) async {
    final current = ref.read(authControllerProvider).user?.username ?? '';
    final controller = TextEditingController(text: current);
    final messenger = ScaffoldMessenger.of(context);
    final saved = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('设置微光号'),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLength: 32,
          decoration: const InputDecoration(
            hintText: '6-32 位，小写字母开头，可含数字和下划线',
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
    final username = controller.text.trim();
    controller.dispose();
    if (saved != true) return;
    final ok = await ref
        .read(authControllerProvider.notifier)
        .updateUsername(username);
    messenger.showSnackBar(
      SnackBar(content: Text(ok ? '微光号已更新' : '微光号设置失败，请检查格式或是否被占用')),
    );
  }

  Future<void> _updateFriendVerify(
    BuildContext context,
    WidgetRef ref,
    bool required,
  ) async {
    final messenger = ScaffoldMessenger.of(context);
    final ok = await ref
        .read(authControllerProvider.notifier)
        .updateFriendVerifyRequired(required);
    messenger.showSnackBar(
      SnackBar(content: Text(ok ? '好友验证设置已更新' : '设置失败，请稍后重试')),
    );
  }

  Future<void> _editAgentStatus(
    BuildContext context,
    WidgetRef ref,
    int current,
  ) async {
    final picked = await showModalBottomSheet<int>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _AgentStatusTile(value: 0, current: current),
            _AgentStatusTile(value: 1, current: current),
            _AgentStatusTile(value: 2, current: current),
          ],
        ),
      ),
    );
    if (picked == null) return;
    if (!context.mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    final ok = await ref
        .read(authControllerProvider.notifier)
        .updateAgentStatus(picked);
    if (!context.mounted) return;
    messenger.showSnackBar(
      SnackBar(content: Text(ok ? '坐席状态已更新' : '坐席状态更新失败')),
    );
  }

  String _agentStatusLabel(int status) => switch (status) {
        1 => '在线',
        2 => '忙碌',
        _ => '离线',
      };
}

class _AgentStatusTile extends StatelessWidget {
  const _AgentStatusTile({required this.value, required this.current});

  final int value;
  final int current;

  @override
  Widget build(BuildContext context) {
    final selected = value == current;
    return ListTile(
      leading: Icon(_icon, color: selected ? LumoColors.primary : null),
      title: Text(_label),
      trailing: selected ? const Icon(Icons.check_rounded) : null,
      onTap: () => Navigator.of(context).pop(value),
    );
  }

  IconData get _icon => switch (value) {
        1 => Icons.radio_button_checked_rounded,
        2 => Icons.pause_circle_outline_rounded,
        _ => Icons.radio_button_unchecked_rounded,
      };

  String get _label => switch (value) {
        1 => '在线',
        2 => '忙碌',
        _ => '离线',
      };
}
