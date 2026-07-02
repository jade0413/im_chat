import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_webrtc/flutter_webrtc.dart';

import '../../core/logging.dart';
import '../../core/proto/codec.dart' as pb;
import '../../core/utils/id.dart';
import '../../core/utils/uuid.dart';

/// 通话阶段（客户端侧状态机，服务端权威态见 docs/call-service-design.md §8）。
enum CallPhase { idle, outgoing, incoming, connecting, active, ended }

enum CallEndReason {
  hangup,
  rejected,
  canceled,
  timeout,
  busy,
  peerOffline,
  answeredElsewhere,
  error,
}

class CallState {
  const CallState({
    this.phase = CallPhase.idle,
    this.callId = '',
    this.peerUserId = '',
    this.peerName = '',
    this.isCaller = false,
    this.muted = false,
    this.endReason,
    this.connectedAt,
  });

  final CallPhase phase;
  final String callId;
  final String peerUserId;
  final String peerName;
  final bool isCaller;
  final bool muted;
  final CallEndReason? endReason;
  final DateTime? connectedAt;

  CallState copyWith({
    CallPhase? phase,
    String? callId,
    String? peerUserId,
    String? peerName,
    bool? isCaller,
    bool? muted,
    CallEndReason? endReason,
    DateTime? connectedAt,
  }) =>
      CallState(
        phase: phase ?? this.phase,
        callId: callId ?? this.callId,
        peerUserId: peerUserId ?? this.peerUserId,
        peerName: peerName ?? this.peerName,
        isCaller: isCaller ?? this.isCaller,
        muted: muted ?? this.muted,
        endReason: endReason ?? this.endReason,
        connectedAt: connectedAt ?? this.connectedAt,
      );
}

/// 1v1 语音通话引擎（D45）：
/// 信令（CALL_* 帧）经 ImSocket 收发，媒体经 flutter_webrtc（P2P + coturn 中继）。
///
/// 分层约定与 ImEngine 相同：不 import UI/DB；对外只暴露 states 流与动作方法。
class CallEngine {
  CallEngine({required this.sendFrame});

  /// 发帧函数（注入 ImSocket.send，保持与连接层解耦）。
  final bool Function(pb.Cmd cmd, Uint8List body) sendFrame;

  final _log = appLogger('call');
  final _stateCtrl = StreamController<CallState>.broadcast();

  CallState _state = const CallState();
  CallState get state => _state;
  Stream<CallState> get states => _stateCtrl.stream;

  RTCPeerConnection? _pc;
  MediaStream? _localStream;
  List<Map<String, dynamic>> _iceServers = const [];
  final List<RTCIceCandidate> _pendingCandidates = [];
  bool _remoteDescriptionSet = false;
  String _clientCallId = '';
  Timer? _endedResetTimer;

  void _emit(CallState next) {
    _state = next;
    if (!_stateCtrl.isClosed) _stateCtrl.add(next);
  }

  // ─── 用户动作 ───────────────────────────────────────────

  /// 拨打。返回 false = 当前不在空闲态或信令未发出。
  Future<bool> startCall(String peerUserId, {String peerName = ''}) async {
    if (_state.phase != CallPhase.idle) return false;
    _clientCallId = createUuid();
    final body = (pb.CallInvite()
          ..calleeUserId = Ids.toInt64(peerUserId)
          ..media = pb.CallMediaType.CALL_MEDIA_VOICE
          ..clientCallId = _clientCallId)
        .writeToBuffer();
    if (!sendFrame(pb.Cmd.CALL_INVITE, body)) return false;
    _emit(CallState(
      phase: CallPhase.outgoing,
      peerUserId: peerUserId,
      peerName: peerName,
      isCaller: true,
    ));
    return true;
  }

  /// 接听来电（ICE 配置已随 CALL_NOTIFY(INVITE) 下发）。
  Future<void> accept() async {
    if (_state.phase != CallPhase.incoming) return;
    sendFrame(
      pb.Cmd.CALL_ANSWER,
      (pb.CallAnswer()
            ..callId = _state.callId
            ..accept = true)
          .writeToBuffer(),
    );
    _emit(_state.copyWith(phase: CallPhase.connecting));
  }

  Future<void> reject() async {
    if (_state.phase != CallPhase.incoming) return;
    sendFrame(
      pb.Cmd.CALL_ANSWER,
      (pb.CallAnswer()
            ..callId = _state.callId
            ..accept = false)
          .writeToBuffer(),
    );
    await _finish(CallEndReason.rejected, notifyServer: false);
  }

  Future<void> hangup() async {
    if (_state.phase == CallPhase.idle || _state.phase == CallPhase.ended) {
      return;
    }
    await _finish(CallEndReason.hangup, notifyServer: true);
  }

  Future<void> toggleMute() async {
    final stream = _localStream;
    if (stream == null) return;
    final next = !_state.muted;
    for (final track in stream.getAudioTracks()) {
      track.enabled = !next;
    }
    _emit(_state.copyWith(muted: next));
  }

  // ─── 下行分发（ImEngine 接线调用）────────────────────────

  /// CALL_ACK：CALL_* 上行的同步响应。主要消费 INVITE 的结果（callId/忙线/离线）。
  void onCallAck(pb.CallAck ack) {
    if (_state.phase == CallPhase.outgoing && _state.callId.isEmpty) {
      if (ack.code == 0) {
        _iceServers = _mapIceServers(ack.iceServers);
        _emit(_state.copyWith(callId: ack.callId));
      } else {
        _log.info('invite rejected by server code=${ack.code}');
        unawaited(_finish(_inviteFailReason(ack.code), notifyServer: false));
      }
      return;
    }
    if (ack.code != 0) {
      // 其他上行失败（如对已结束通话继续 SIGNAL）：通话已不存在则本地收尾
      if (ack.code == 7002 && _state.phase != CallPhase.idle) {
        unawaited(_finish(CallEndReason.error, notifyServer: false));
      }
      _log.warning('call ack error code=${ack.code} msg=${ack.message}');
    }
  }

  void onCallNotify(pb.CallNotify notify) {
    switch (notify.event) {
      case pb.CallEvent.CALL_EVENT_INVITE:
        _onIncomingInvite(notify);
      case pb.CallEvent.CALL_EVENT_ACCEPTED:
        unawaited(_onAccepted(notify));
      case pb.CallEvent.CALL_EVENT_SIGNAL:
        unawaited(_onSignal(notify.signal));
      case pb.CallEvent.CALL_EVENT_REJECTED:
        unawaited(_finish(CallEndReason.rejected, notifyServer: false));
      case pb.CallEvent.CALL_EVENT_CANCELED:
        unawaited(_finish(CallEndReason.canceled, notifyServer: false));
      case pb.CallEvent.CALL_EVENT_HANGUP:
        unawaited(_finish(CallEndReason.hangup, notifyServer: false));
      case pb.CallEvent.CALL_EVENT_TIMEOUT:
        unawaited(_finish(CallEndReason.timeout, notifyServer: false));
      case pb.CallEvent.CALL_EVENT_BUSY:
        unawaited(_finish(CallEndReason.busy, notifyServer: false));
      case pb.CallEvent.CALL_EVENT_ANSWERED_ELSEWHERE:
        if (_state.phase == CallPhase.incoming) {
          unawaited(
              _finish(CallEndReason.answeredElsewhere, notifyServer: false));
        }
      default:
        _log.fine('unhandled call event ${notify.event}');
    }
  }

  // ─── 信令流转 ───────────────────────────────────────────

  void _onIncomingInvite(pb.CallNotify notify) {
    if (_state.phase != CallPhase.idle) {
      // 本地已在通话/振铃（服务端忙线窗口内的竞态来电）：自动拒接
      sendFrame(
        pb.Cmd.CALL_ANSWER,
        (pb.CallAnswer()
              ..callId = notify.callId
              ..accept = false)
            .writeToBuffer(),
      );
      return;
    }
    _iceServers = _mapIceServers(notify.iceServers);
    _emit(CallState(
      phase: CallPhase.incoming,
      callId: notify.callId,
      peerUserId: Ids.toStr(notify.peerUserId),
      peerName: '用户 ${Ids.toStr(notify.peerUserId)}',
      isCaller: false,
    ));
  }

  /// 主叫收到 ACCEPTED：建 PC → createOffer → 发 SDP_OFFER。
  Future<void> _onAccepted(pb.CallNotify notify) async {
    if (!_state.isCaller || _state.phase != CallPhase.outgoing) return;
    if (notify.iceServers.isNotEmpty) {
      _iceServers = _mapIceServers(notify.iceServers);
    }
    _emit(_state.copyWith(phase: CallPhase.connecting));
    try {
      await _setupPeer();
      final offer = await _pc!.createOffer({});
      await _pc!.setLocalDescription(offer);
      _sendSignal(pb.CallSignalType.CALL_SDP_OFFER, offer.sdp ?? '');
    } catch (e) {
      _log.warning('offer failed: $e');
      await _finish(CallEndReason.error, notifyServer: true);
    }
  }

  Future<void> _onSignal(pb.CallSignal signal) async {
    if (signal.callId != _state.callId) return; // 非当前通话，忽略
    try {
      switch (signal.type) {
        case pb.CallSignalType.CALL_SDP_OFFER: // 被叫
          await _setupPeer();
          await _pc!.setRemoteDescription(
              RTCSessionDescription(signal.payload, 'offer'));
          _remoteDescriptionSet = true;
          await _drainPendingCandidates();
          final answer = await _pc!.createAnswer({});
          await _pc!.setLocalDescription(answer);
          _sendSignal(pb.CallSignalType.CALL_SDP_ANSWER, answer.sdp ?? '');
        case pb.CallSignalType.CALL_SDP_ANSWER: // 主叫
          await _pc?.setRemoteDescription(
              RTCSessionDescription(signal.payload, 'answer'));
          _remoteDescriptionSet = true;
          await _drainPendingCandidates();
        case pb.CallSignalType.CALL_ICE_CANDIDATE:
          final map = jsonDecode(signal.payload) as Map<String, dynamic>;
          final candidate = RTCIceCandidate(
            map['candidate'] as String?,
            map['sdpMid'] as String?,
            map['sdpMLineIndex'] as int?,
          );
          if (_pc == null || !_remoteDescriptionSet) {
            _pendingCandidates.add(candidate); // trickle 先于 SDP 到达时缓存
          } else {
            await _pc!.addCandidate(candidate);
          }
        default:
          _log.fine('unknown signal type ${signal.type}');
      }
    } catch (e) {
      _log.warning('signal handling failed: $e');
      await _finish(CallEndReason.error, notifyServer: true);
    }
  }

  Future<void> _drainPendingCandidates() async {
    for (final c in _pendingCandidates) {
      await _pc?.addCandidate(c);
    }
    _pendingCandidates.clear();
  }

  void _sendSignal(pb.CallSignalType type, String payload) {
    sendFrame(
      pb.Cmd.CALL_SIGNAL,
      (pb.CallSignal()
            ..callId = _state.callId
            ..type = type
            ..payload = payload)
          .writeToBuffer(),
    );
  }

  // ─── WebRTC ─────────────────────────────────────────────

  Future<void> _setupPeer() async {
    if (_pc != null) return;
    _remoteDescriptionSet = false;
    final pc = await createPeerConnection({
      'iceServers': _iceServers,
      'sdpSemantics': 'unified-plan',
    });
    _pc = pc;

    final stream = await navigator.mediaDevices
        .getUserMedia({'audio': true, 'video': false});
    _localStream = stream;
    for (final track in stream.getAudioTracks()) {
      await pc.addTrack(track, stream);
    }

    pc.onIceCandidate = (candidate) {
      if (candidate.candidate == null) return;
      _sendSignal(
        pb.CallSignalType.CALL_ICE_CANDIDATE,
        jsonEncode({
          'candidate': candidate.candidate,
          'sdpMid': candidate.sdpMid,
          'sdpMLineIndex': candidate.sdpMLineIndex,
        }),
      );
    };
    pc.onConnectionState = (state) {
      _log.info('pc state: $state');
      switch (state) {
        case RTCPeerConnectionState.RTCPeerConnectionStateConnected:
          if (_state.phase == CallPhase.connecting) {
            _emit(_state.copyWith(
                phase: CallPhase.active, connectedAt: DateTime.now()));
          }
        case RTCPeerConnectionState.RTCPeerConnectionStateFailed:
          unawaited(_finish(CallEndReason.error, notifyServer: true));
        default:
          break;
      }
    };
    // 音频轨自动播放，无需 renderer；免提/听筒切换二阶段（Helper.setSpeakerphoneOn）
  }

  // ─── 收尾 ───────────────────────────────────────────────

  Future<void> _finish(CallEndReason reason, {required bool notifyServer}) async {
    if (_state.phase == CallPhase.idle) return;
    if (notifyServer && _state.callId.isNotEmpty) {
      sendFrame(
        pb.Cmd.CALL_HANGUP,
        (pb.CallHangup()..callId = _state.callId).writeToBuffer(),
      );
    }
    await _teardownMedia();
    _emit(_state.copyWith(phase: CallPhase.ended, endReason: reason));
    _endedResetTimer?.cancel();
    _endedResetTimer = Timer(const Duration(seconds: 2), () {
      _emit(const CallState()); // ended 停留 2s 供 UI 展示原因，随后回 idle
    });
  }

  Future<void> _teardownMedia() async {
    _pendingCandidates.clear();
    _remoteDescriptionSet = false;
    _clientCallId = '';
    final pc = _pc;
    _pc = null;
    final stream = _localStream;
    _localStream = null;
    try {
      for (final track in stream?.getTracks() ?? const []) {
        await track.stop();
      }
      await stream?.dispose();
      await pc?.close();
    } catch (e) {
      _log.fine('teardown error: $e');
    }
  }

  List<Map<String, dynamic>> _mapIceServers(List<pb.IceServer> servers) {
    return servers
        .map((s) => <String, dynamic>{
              'urls': s.urls.toList(),
              if (s.username.isNotEmpty) 'username': s.username,
              if (s.credential.isNotEmpty) 'credential': s.credential,
            })
        .toList();
  }

  CallEndReason _inviteFailReason(int code) => switch (code) {
        7001 => CallEndReason.busy,
        7004 => CallEndReason.peerOffline,
        _ => CallEndReason.error,
      };

  Future<void> dispose() async {
    _endedResetTimer?.cancel();
    await _teardownMedia();
    await _stateCtrl.close();
  }
}
