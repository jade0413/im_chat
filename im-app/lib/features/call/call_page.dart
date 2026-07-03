import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_webrtc/flutter_webrtc.dart';
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
  late final RTCVideoRenderer _localRenderer;
  bool _renderersReady = false;
  MediaStream? _boundLocalStream;
  final _remoteRenderers = <String, RTCVideoRenderer>{};
  final _remoteRendererReady = <String>{};
  final _initializingRemoteRenderers = <String>{};
  final _boundRemoteStreams = <String, MediaStream>{};

  @override
  void initState() {
    super.initState();
    _localRenderer = RTCVideoRenderer();
    unawaited(_initRenderers());
  }

  @override
  void dispose() {
    _ticker?.cancel();
    _localRenderer.dispose();
    for (final renderer in _remoteRenderers.values) {
      renderer.dispose();
    }
    super.dispose();
  }

  Future<void> _initRenderers() async {
    await _localRenderer.initialize();
    if (mounted) setState(() => _renderersReady = true);
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

  void _syncRenderers(CallState state) {
    if (!_renderersReady) return;
    if (state.media != CallMedia.video) {
      if (_boundLocalStream != null) {
        _boundLocalStream = null;
        _localRenderer.srcObject = null;
      }
      _clearRemoteRenderers();
      return;
    }
    if (_boundLocalStream != state.localStream) {
      _boundLocalStream = state.localStream;
      _localRenderer.srcObject = state.localStream;
    }
    final streams = _remoteVideoStreams(state);
    final stale = _remoteRenderers.keys
        .where((userId) => !streams.containsKey(userId))
        .toList();
    for (final userId in stale) {
      _disposeRemoteRenderer(userId);
    }
    for (final entry in streams.entries) {
      _ensureRemoteRenderer(entry.key);
      if (_remoteRendererReady.contains(entry.key) &&
          _boundRemoteStreams[entry.key] != entry.value) {
        _boundRemoteStreams[entry.key] = entry.value;
        _remoteRenderers[entry.key]?.srcObject = entry.value;
      }
    }
  }

  Map<String, MediaStream> _remoteVideoStreams(CallState state) {
    if (state.remoteStreams.isNotEmpty) return state.remoteStreams;
    final stream = state.remoteStream;
    if (stream == null) return const {};
    final userId = state.peerUserId.isEmpty ? 'peer' : state.peerUserId;
    return {userId: stream};
  }

  void _ensureRemoteRenderer(String userId) {
    if (_remoteRenderers.containsKey(userId) ||
        _initializingRemoteRenderers.contains(userId)) {
      return;
    }
    final renderer = RTCVideoRenderer();
    _remoteRenderers[userId] = renderer;
    _initializingRemoteRenderers.add(userId);
    unawaited(renderer.initialize().then((_) {
      _initializingRemoteRenderers.remove(userId);
      _remoteRendererReady.add(userId);
      final stream = _boundRemoteStreams[userId];
      if (stream != null) renderer.srcObject = stream;
      if (mounted) setState(() {});
    }));
  }

  void _disposeRemoteRenderer(String userId) {
    _initializingRemoteRenderers.remove(userId);
    _remoteRendererReady.remove(userId);
    _boundRemoteStreams.remove(userId);
    final renderer = _remoteRenderers.remove(userId);
    renderer?.srcObject = null;
    renderer?.dispose();
  }

  void _clearRemoteRenderers() {
    final keys = _remoteRenderers.keys.toList();
    for (final key in keys) {
      _disposeRemoteRenderer(key);
    }
  }

  @override
  Widget build(BuildContext context) {
    final engine = ref.watch(callEngineProvider);
    final state = ref.watch(callStateProvider).valueOrNull ?? engine.state;
    _syncTicker(state);
    _syncRenderers(state);

    // 通话彻底结束回 idle → 关闭本页
    ref.listen(callStateProvider, (_, next) {
      final s = next.valueOrNull;
      if (s != null &&
          s.phase == CallPhase.idle &&
          mounted &&
          context.canPop()) {
        context.pop();
      }
    });

    final theme = Theme.of(context);
    return Scaffold(
      backgroundColor: state.media == CallMedia.video
          ? Colors.black
          : theme.colorScheme.surfaceContainerHighest,
      body: SafeArea(
        child: state.media == CallMedia.video
            ? _videoLayout(state, engine, theme)
            : _voiceLayout(state, engine, theme),
      ),
    );
  }

  Widget _voiceLayout(CallState state, CallEngine engine, ThemeData theme) {
    final title = _callTitle(state);
    return Column(
      children: [
        const Spacer(flex: 2),
        LumoAvatar(name: title, size: 96),
        const SizedBox(height: 16),
        Text(title, style: theme.textTheme.headlineSmall),
        const SizedBox(height: 8),
        Text(_statusText(state), style: theme.textTheme.bodyLarge),
        const Spacer(flex: 3),
        _actions(state, engine),
        const SizedBox(height: 48),
      ],
    );
  }

  Widget _videoLayout(CallState state, CallEngine engine, ThemeData theme) {
    return Stack(
      children: [
        Positioned.fill(child: _remoteVideoOrFallback(state, theme)),
        Positioned(
          left: 18,
          right: 18,
          top: 16,
          child: DefaultTextStyle(
            style: const TextStyle(color: Colors.white),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _callTitle(state),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style:
                      theme.textTheme.titleLarge?.copyWith(color: Colors.white),
                ),
                const SizedBox(height: 4),
                Text(
                  _statusText(state),
                  style: theme.textTheme.bodyMedium
                      ?.copyWith(color: Colors.white70),
                ),
              ],
            ),
          ),
        ),
        if (_renderersReady && state.localStream != null)
          Positioned(
            right: 16,
            top: 82,
            child: ClipRRect(
              borderRadius: BorderRadius.circular(14),
              child: Container(
                width: 118,
                height: 166,
                color: Colors.black54,
                child: RTCVideoView(
                  _localRenderer,
                  mirror: true,
                  objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitCover,
                ),
              ),
            ),
          ),
        Positioned(
          left: 0,
          right: 0,
          bottom: 48,
          child: _actions(state, engine),
        ),
      ],
    );
  }

  Widget _remoteVideoOrFallback(CallState state, ThemeData theme) {
    final title = _callTitle(state);
    final streams = _remoteVideoStreams(state);
    if (_renderersReady && streams.isNotEmpty) {
      if (state.isGroupCall && streams.length > 1) {
        return _remoteVideoGrid(streams, theme);
      }
      final entry = streams.entries.first;
      final renderer = _remoteRenderers[entry.key];
      if (renderer != null && _remoteRendererReady.contains(entry.key)) {
        return RTCVideoView(
          renderer,
          objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitCover,
        );
      }
    }
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          LumoAvatar(name: title, size: 96),
          const SizedBox(height: 16),
          Text(
            title,
            style: theme.textTheme.headlineSmall?.copyWith(color: Colors.white),
          ),
          const SizedBox(height: 8),
          Text(
            _statusText(state),
            style: theme.textTheme.bodyLarge?.copyWith(color: Colors.white70),
          ),
        ],
      ),
    );
  }

  Widget _remoteVideoGrid(
    Map<String, MediaStream> streams,
    ThemeData theme,
  ) {
    final crossAxisCount = streams.length <= 2 ? streams.length : 2;
    return GridView.count(
      crossAxisCount: crossAxisCount,
      childAspectRatio: 9 / 16,
      padding: const EdgeInsets.only(top: 72, bottom: 140, left: 8, right: 8),
      mainAxisSpacing: 8,
      crossAxisSpacing: 8,
      children: [
        for (final entry in streams.entries)
          ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: DecoratedBox(
              decoration: const BoxDecoration(color: Colors.black54),
              child: _remoteVideoTile(entry.key, theme),
            ),
          ),
      ],
    );
  }

  Widget _remoteVideoTile(String userId, ThemeData theme) {
    final renderer = _remoteRenderers[userId];
    if (renderer != null && _remoteRendererReady.contains(userId)) {
      return RTCVideoView(
        renderer,
        objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitCover,
      );
    }
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          LumoAvatar(name: '用户 $userId', size: 64),
          const SizedBox(height: 8),
          Text(
            '用户 $userId',
            style: theme.textTheme.bodyMedium?.copyWith(color: Colors.white70),
          ),
        ],
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
            if (state.media == CallMedia.video)
              _round(
                icon: state.cameraEnabled ? Icons.videocam : Icons.videocam_off,
                color: Colors.blueGrey,
                label: state.cameraEnabled ? '摄像头' : '已关闭',
                onTap: engine.toggleCamera,
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
        CallPhase.outgoing => state.isGroupCall
            ? (state.media == CallMedia.video ? '正在发起群视频…' : '正在发起群语音…')
            : (state.media == CallMedia.video ? '正在发起视频通话…' : '正在呼叫…'),
        CallPhase.incoming => state.isGroupCall
            ? (state.media == CallMedia.video ? '邀请你加入群视频' : '邀请你加入群语音')
            : (state.media == CallMedia.video ? '邀请你视频通话' : '邀请你语音通话'),
        CallPhase.connecting => '接通中…',
        CallPhase.active => _fmt(_elapsed),
        CallPhase.ended => _endText(state.endReason),
        CallPhase.idle => '',
      };

  String _callTitle(CallState state) {
    if (!state.isGroupCall) return state.peerName;
    final title = state.peerName.isEmpty ? '群通话' : state.peerName;
    final count = state.participantUserIds.length;
    return count > 0 ? '$title · $count 人' : title;
  }

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
