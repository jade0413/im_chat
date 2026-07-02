import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/providers.dart';
import '../../data/call/call_engine.dart';
import '../../shared/widgets/lumo_avatar.dart';

/// 语音通话页（D45 MVP）：来电/去电/通话中共用，随 CallEngine 状态流切换。
/// ended → idle 时自动退出。
class CallPage extends ConsumerStatefulWidget {
  const CallPage({super.key});

  @override
  ConsumerState<CallPage> createState() => _CallPageState();
}

class _CallPageState extends ConsumerState<CallPage> {
  Timer? _ticker;
  Duration _elapsed = Duration.zero;

  @override
  void dispose() {
    _ticker?.cancel();
    super.dispose();
  }

  void _syncTicker(CallState state) {
    if (state.phase == CallPhase.active && _ticker == null) {
      _ticker = Timer.periodic(const Duration(seconds: 1), (_) {
        final startedAt = state.connectedAt;
        if (startedAt != null && mounted) {
          setState(() => _elapsed = DateTime.now().difference(startedAt));
        }
      });
    } else if (state.phase != CallPhase.active) {
      _ticker?.cancel();
      _ticker = null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final engine = ref.watch(callEngineProvider);
    final state = ref.watch(callStateProvider).valueOrNull ?? engine.state;
    _syncTicker(state);

    // 通话彻底结束回 idle → 关闭本页
    ref.listen(callStateProvider, (_, next) {
      final s = next.valueOrNull;
      if (s != null && s.phase == CallPhase.idle && mounted && context.canPop()) {
        context.pop();
      }
    });

    final theme = Theme.of(context);
    return Scaffold(
      backgroundColor: theme.colorScheme.surfaceContainerHighest,
      body: SafeArea(
        child: Column(
          children: [
            const Spacer(flex: 2),
            LumoAvatar(name: state.peerName, size: 96),
            const SizedBox(height: 16),
            Text(state.peerName, style: theme.textTheme.headlineSmall),
            const SizedBox(height: 8),
            Text(_statusText(state), style: theme.textTheme.bodyLarge),
            const Spacer(flex: 3),
            _actions(state, engine),
            const SizedBox(height: 48),
          ],
        ),
      ),
    );
  }

  Widget _actions(CallState state, CallEngine engine) {
    switch (state.phase) {
      case CallPhase.incoming:
        return Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            _round(
              icon: Icons.call_end,
              color: Colors.red,
              label: '拒绝',
              onTap: engine.reject,
            ),
            _round(
              icon: Icons.call,
              color: Colors.green,
              label: '接听',
              onTap: engine.accept,
            ),
          ],
        );
      case CallPhase.outgoing:
      case CallPhase.connecting:
      case CallPhase.active:
        return Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            _round(
              icon: state.muted ? Icons.mic_off : Icons.mic,
              color: Colors.blueGrey,
              label: state.muted ? '已静音' : '静音',
              onTap: engine.toggleMute,
            ),
            _round(
              icon: Icons.call_end,
              color: Colors.red,
              label: '挂断',
              onTap: engine.hangup,
            ),
          ],
        );
      case CallPhase.ended:
      case CallPhase.idle:
        return const SizedBox(height: 72);
    }
  }

  Widget _round({
    required IconData icon,
    required Color color,
    required String label,
    required VoidCallback onTap,
  }) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        FloatingActionButton(
          heroTag: label,
          backgroundColor: color,
          onPressed: onTap,
          child: Icon(icon, color: Colors.white),
        ),
        const SizedBox(height: 8),
        Text(label),
      ],
    );
  }

  String _statusText(CallState state) => switch (state.phase) {
        CallPhase.outgoing => '正在呼叫…',
        CallPhase.incoming => '邀请你语音通话',
        CallPhase.connecting => '接通中…',
        CallPhase.active => _fmt(_elapsed),
        CallPhase.ended => _endText(state.endReason),
        CallPhase.idle => '',
      };

  String _endText(CallEndReason? reason) => switch (reason) {
        CallEndReason.rejected => '对方已拒绝',
        CallEndReason.canceled => '对方已取消',
        CallEndReason.timeout => '无人接听',
        CallEndReason.busy => '对方忙线中',
        CallEndReason.peerOffline => '对方不在线',
        CallEndReason.answeredElsewhere => '已在其他设备接听',
        CallEndReason.error => '通话异常结束',
        _ => '通话已结束',
      };

  String _fmt(Duration d) {
    final m = d.inMinutes.toString().padLeft(2, '0');
    final s = (d.inSeconds % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }
}
