import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';
import '../../core/theme/lumo_colors.dart';
import '../../data/models/enums.dart' as model;

/// 顶部连接状态横幅：仅在非 connected 时显示一条细提示。
class ConnectionBanner extends ConsumerWidget {
  const ConnectionBanner({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(connectionStateProvider).valueOrNull;
    final (label, color, show) = switch (state) {
      model.ConnectionState.connecting => (
          '连接中…',
          LumoColors.textSecondary,
          true
        ),
      model.ConnectionState.authenticating => (
          '鉴权中…',
          LumoColors.textSecondary,
          true
        ),
      model.ConnectionState.reconnecting => (
          '网络不稳定，重连中…',
          LumoColors.danger,
          true
        ),
      model.ConnectionState.error => ('连接异常，正在重试…', LumoColors.danger, true),
      model.ConnectionState.closed => ('已离线', LumoColors.textSecondary, true),
      _ => ('', LumoColors.success, false),
    };
    if (!show) return const SizedBox.shrink();
    return Container(
      width: double.infinity,
      color: color.withValues(alpha: 0.12),
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Text(
        label,
        textAlign: TextAlign.center,
        style:
            TextStyle(fontSize: 12, color: color, fontWeight: FontWeight.w500),
      ),
    );
  }
}
