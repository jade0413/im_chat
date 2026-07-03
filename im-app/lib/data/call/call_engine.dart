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

enum CallMedia { voice, video }

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
    this.groupId = '',
    this.participantUserIds = const [],
    this.isCaller = false,
    this.media = CallMedia.voice,
    this.muted = false,
    this.cameraEnabled = false,
    this.endReason,
    this.connectedAt,
    this.localStream,
    this.remoteStream,
    this.remoteStreams = const {},
  });

  final CallPhase phase;
  final String callId;
  final String peerUserId;
  final String peerName;
  final String groupId;
  final List<String> participantUserIds;
  final bool isCaller;
  final CallMedia media;
  final bool muted;
  final bool cameraEnabled;
  final CallEndReason? endReason;
  final DateTime? connectedAt;
  final MediaStream? localStream;
  final MediaStream? remoteStream;
  final Map<String, MediaStream> remoteStreams;

  bool get isGroupCall => groupId.isNotEmpty && groupId != '0';

  CallState copyWith({
    CallPhase? phase,
    String? callId,
    String? peerUserId,
    String? peerName,
    String? groupId,
    List<String>? participantUserIds,
    bool? isCaller,
    CallMedia? media,
    bool? muted,
    bool? cameraEnabled,
    CallEndReason? endReason,
    DateTime? connectedAt,
    MediaStream? localStream,
    MediaStream? remoteStream,
    Map<String, MediaStream>? remoteStreams,
    bool clearLocalStream = false,
    bool clearRemoteStream = false,
  }) =>
      CallState(
        phase: phase ?? this.phase,
        callId: callId ?? this.callId,
        peerUserId: peerUserId ?? this.peerUserId,
        peerName: peerName ?? this.peerName,
        groupId: groupId ?? this.groupId,
        participantUserIds: participantUserIds ?? this.participantUserIds,
        isCaller: isCaller ?? this.isCaller,
        media: media ?? this.media,
        muted: muted ?? this.muted,
        cameraEnabled: cameraEnabled ?? this.cameraEnabled,
        endReason: endReason ?? this.endReason,
        connectedAt: connectedAt ?? this.connectedAt,
        localStream:
            clearLocalStream ? null : (localStream ?? this.localStream),
        remoteStream:
            clearRemoteStream ? null : (remoteStream ?? this.remoteStream),
        remoteStreams: remoteStreams ?? this.remoteStreams,
      );
}

/// 1v1/小群音视频通话引擎（D45/D50）：
/// 信令（CALL_* 帧）经 ImSocket 收发，媒体经 flutter_webrtc（P2P/mesh + coturn 中继）。
///
/// 分层约定与 ImEngine 相同：不 import UI/DB；对外只暴露 states 流与动作方法。
class CallEngine {
  CallEngine({
    required this.sendFrame,
    Duration incomingTimeout = const Duration(seconds: 60),
  }) : _incomingTimeout = incomingTimeout;

  /// 发帧函数（注入 ImSocket.send，保持与连接层解耦）。
  final bool Function(pb.Cmd cmd, Uint8List body) sendFrame;
  final Duration _incomingTimeout;

  final _log = appLogger('call');
  final _stateCtrl = StreamController<CallState>.broadcast();

  CallState _state = const CallState();
  CallState get state => _state;
  Stream<CallState> get states => _stateCtrl.stream;

  MediaStream? _localStream;
  List<Map<String, dynamic>> _iceServers = const [];
  final _peers = <String, _PeerSlot>{};
  String _clientCallId = '';
  Timer? _endedResetTimer;
  Timer? _incomingTimeoutTimer;

  void _emit(CallState next) {
    _state = next;
    if (!_stateCtrl.isClosed) _stateCtrl.add(next);
  }

  // ─── 用户动作 ───────────────────────────────────────────

  /// 拨打。返回 false = 当前不在空闲态或信令未发出。
  Future<bool> startCall(
    String peerUserId, {
    String peerName = '',
    CallMedia media = CallMedia.voice,
  }) async {
    if (_state.phase != CallPhase.idle) return false;
    _clientCallId = createUuid();
    final body = (pb.CallInvite()
          ..calleeUserId = Ids.toInt64(peerUserId)
          ..media = _mediaToProto(media)
          ..clientCallId = _clientCallId)
        .writeToBuffer();
    if (!sendFrame(pb.Cmd.CALL_INVITE, body)) return false;
    _emit(CallState(
      phase: CallPhase.outgoing,
      peerUserId: peerUserId,
      peerName: peerName,
      isCaller: true,
      media: media,
    ));
    return true;
  }

  /// 发起群音视频。当前客户端先完成群通话信令入口；媒体层仍按服务端下发的
  /// 对端信令建立连接，后续可在此基础上扩展多 PeerConnection。
  Future<bool> startGroupCall(
    String groupId, {
    String groupName = '',
    CallMedia media = CallMedia.voice,
  }) async {
    if (_state.phase != CallPhase.idle) return false;
    _clientCallId = createUuid();
    final body = (pb.CallInvite()
          ..groupId = Ids.toInt64(groupId)
          ..media = _mediaToProto(media)
          ..clientCallId = _clientCallId)
        .writeToBuffer();
    if (!sendFrame(pb.Cmd.CALL_INVITE, body)) return false;
    _emit(CallState(
      phase: CallPhase.outgoing,
      peerName: groupName,
      groupId: groupId,
      isCaller: true,
      media: media,
    ));
    return true;
  }

  /// 接听来电（ICE 配置已随 CALL_NOTIFY(INVITE) 下发）。
  Future<void> accept() async {
    if (_state.phase != CallPhase.incoming) return;
    _cancelIncomingTimeout();
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
    _cancelIncomingTimeout();
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

  Future<void> toggleCamera() async {
    final stream = _localStream;
    if (stream == null || _state.media != CallMedia.video) return;
    final next = !_state.cameraEnabled;
    for (final track in stream.getVideoTracks()) {
      track.enabled = next;
    }
    _emit(_state.copyWith(cameraEnabled: next));
  }

  // ─── 下行分发（ImEngine 接线调用）────────────────────────

  /// CALL_ACK：CALL_* 上行的同步响应。主要消费 INVITE 的结果（callId/忙线/离线）。
  void onCallAck(pb.CallAck ack) {
    if (_state.phase == CallPhase.outgoing && _state.callId.isEmpty) {
      if (ack.code == 0) {
        _iceServers = _mapIceServers(ack.iceServers);
        _emit(_state.copyWith(
          callId: ack.callId,
          groupId: Ids.isZeroOrEmpty(Ids.toStr(ack.groupId))
              ? _state.groupId
              : Ids.toStr(ack.groupId),
        ));
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
        unawaited(_onSignal(notify));
      case pb.CallEvent.CALL_EVENT_REJECTED:
        unawaited(_finish(CallEndReason.rejected, notifyServer: false));
      case pb.CallEvent.CALL_EVENT_CANCELED:
        unawaited(_finish(CallEndReason.canceled, notifyServer: false));
      case pb.CallEvent.CALL_EVENT_HANGUP:
        if (_state.isGroupCall) {
          unawaited(_onGroupPeerHangup(notify));
        } else {
          unawaited(_finish(CallEndReason.hangup, notifyServer: false));
        }
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
      peerName: Ids.isZeroOrEmpty(Ids.toStr(notify.groupId))
          ? '用户 ${Ids.toStr(notify.peerUserId)}'
          : '群通话',
      groupId: Ids.toStr(notify.groupId),
      participantUserIds:
          notify.participantUserIds.map((id) => Ids.toStr(id)).toList(),
      isCaller: false,
      media: _mediaFromProto(notify.media),
    ));
    _startIncomingTimeout(notify.callId);
  }

  void _startIncomingTimeout(String callId) {
    _cancelIncomingTimeout();
    _incomingTimeoutTimer = Timer(_incomingTimeout, () {
      if (_state.phase == CallPhase.incoming && _state.callId == callId) {
        unawaited(_finish(CallEndReason.timeout, notifyServer: false));
      }
    });
  }

  void _cancelIncomingTimeout() {
    _incomingTimeoutTimer?.cancel();
    _incomingTimeoutTimer = null;
  }

  /// 收到 ACCEPTED：1v1 主叫建 PC；群通话中已在会成员对新加入成员发 offer。
  Future<void> _onAccepted(pb.CallNotify notify) async {
    if (notify.callId != _state.callId) return;
    if (notify.iceServers.isNotEmpty) {
      _iceServers = _mapIceServers(notify.iceServers);
    }
    final participants = _participantIds(notify);
    final nextPhase = _state.phase == CallPhase.active
        ? CallPhase.active
        : CallPhase.connecting;
    _emit(_state.copyWith(
      phase: nextPhase,
      media: _mediaFromProto(notify.media),
      participantUserIds:
          participants.isEmpty ? _state.participantUserIds : participants,
    ));

    final peerId = _notifyPeerId(notify);
    if (_state.isGroupCall) {
      if (Ids.isZeroOrEmpty(peerId) || _peers.containsKey(peerId)) return;
    } else if (!_state.isCaller || _state.phase != CallPhase.connecting) {
      return;
    }

    try {
      final slot = await _setupPeer(
        _state.isGroupCall ? peerId : _directPeerId(notify),
      );
      final offer = await slot.pc.createOffer({});
      await slot.pc.setLocalDescription(offer);
      _sendSignal(
        pb.CallSignalType.CALL_SDP_OFFER,
        offer.sdp ?? '',
        targetUserId: _state.isGroupCall ? peerId : '',
      );
    } catch (e) {
      _log.warning('offer failed: $e');
      await _finish(CallEndReason.error, notifyServer: true);
    }
  }

  Future<void> _onSignal(pb.CallNotify notify) async {
    final signal = notify.signal;
    if (signal.callId != _state.callId) return; // 非当前通话，忽略
    final peerId =
        _state.isGroupCall ? _notifyPeerId(notify) : _directPeerId(notify);
    if (Ids.isZeroOrEmpty(peerId)) return;
    try {
      switch (signal.type) {
        case pb.CallSignalType.CALL_SDP_OFFER: // 被叫
          final slot = await _setupPeer(peerId);
          await slot.pc.setRemoteDescription(
              RTCSessionDescription(signal.payload, 'offer'));
          slot.remoteDescriptionSet = true;
          await _drainPendingCandidates(slot);
          final answer = await slot.pc.createAnswer({});
          await slot.pc.setLocalDescription(answer);
          _sendSignal(
            pb.CallSignalType.CALL_SDP_ANSWER,
            answer.sdp ?? '',
            targetUserId: _state.isGroupCall ? peerId : '',
          );
        case pb.CallSignalType.CALL_SDP_ANSWER: // 主叫
          final slot = _peers[peerId];
          if (slot == null) return;
          await slot.pc.setRemoteDescription(
              RTCSessionDescription(signal.payload, 'answer'));
          slot.remoteDescriptionSet = true;
          await _drainPendingCandidates(slot);
        case pb.CallSignalType.CALL_ICE_CANDIDATE:
          final map = jsonDecode(signal.payload) as Map<String, dynamic>;
          final candidate = RTCIceCandidate(
            map['candidate'] as String?,
            map['sdpMid'] as String?,
            map['sdpMLineIndex'] as int?,
          );
          final slot = await _setupPeer(peerId);
          if (!slot.remoteDescriptionSet) {
            slot.pendingCandidates.add(candidate); // trickle 先于 SDP 到达时缓存
          } else {
            await slot.pc.addCandidate(candidate);
          }
        default:
          _log.fine('unknown signal type ${signal.type}');
      }
    } catch (e) {
      _log.warning('signal handling failed: $e');
      await _finish(CallEndReason.error, notifyServer: true);
    }
  }

  Future<void> _drainPendingCandidates(_PeerSlot slot) async {
    for (final c in slot.pendingCandidates) {
      await slot.pc.addCandidate(c);
    }
    slot.pendingCandidates.clear();
  }

  void _sendSignal(
    pb.CallSignalType type,
    String payload, {
    String targetUserId = '',
  }) {
    final signal = pb.CallSignal()
      ..callId = _state.callId
      ..type = type
      ..payload = payload;
    if (_state.isGroupCall && !Ids.isZeroOrEmpty(targetUserId)) {
      signal.targetUserId = Ids.toInt64(targetUserId);
    }
    sendFrame(
      pb.Cmd.CALL_SIGNAL,
      signal.writeToBuffer(),
    );
  }

  // ─── WebRTC ─────────────────────────────────────────────

  Future<_PeerSlot> _setupPeer(String peerUserId) async {
    final peerId = Ids.isZeroOrEmpty(peerUserId) ? 'peer' : peerUserId;
    final existing = _peers[peerId];
    if (existing != null) return existing;
    final pc = await createPeerConnection({
      'iceServers': _iceServers,
      'sdpSemantics': 'unified-plan',
    });
    final slot = _PeerSlot(pc);
    _peers[peerId] = slot;

    final stream = await _ensureLocalStream();
    for (final track in stream.getTracks()) {
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
        targetUserId: _state.isGroupCall ? peerId : '',
      );
    };
    pc.onTrack = (event) {
      if (event.streams.isEmpty) return;
      final remote = event.streams.first;
      slot.remoteStream = remote;
      _emit(_state.copyWith(
        remoteStream: _primaryRemoteStream(),
        remoteStreams: _remoteStreamsSnapshot(),
      ));
    };
    pc.onConnectionState = (state) {
      _log.info('pc[$peerId] state: $state');
      switch (state) {
        case RTCPeerConnectionState.RTCPeerConnectionStateConnected:
          if (_state.phase == CallPhase.connecting) {
            _emit(_state.copyWith(
                phase: CallPhase.active, connectedAt: DateTime.now()));
          }
        case RTCPeerConnectionState.RTCPeerConnectionStateFailed:
          if (_state.isGroupCall) {
            unawaited(_removePeer(peerId));
          } else {
            unawaited(_finish(CallEndReason.error, notifyServer: true));
          }
        default:
          break;
      }
    };
    return slot;
  }

  Future<MediaStream> _ensureLocalStream() async {
    final existing = _localStream;
    if (existing != null) return existing;
    final video = _state.media == CallMedia.video;
    final stream = await navigator.mediaDevices.getUserMedia({
      'audio': true,
      'video': video ? {'facingMode': 'user'} : false,
    });
    _localStream = stream;
    _emit(_state.copyWith(
      localStream: stream,
      cameraEnabled: video && stream.getVideoTracks().isNotEmpty,
    ));
    // 音频轨自动播放；视频轨交给 CallPage 的 RTCVideoRenderer 渲染。
    return stream;
  }

  Future<void> _removePeer(String peerUserId) async {
    final slot = _peers.remove(peerUserId);
    if (slot == null) {
      _emit(_state.copyWith(
        participantUserIds:
            _state.participantUserIds.where((id) => id != peerUserId).toList(),
      ));
      return;
    }
    try {
      await slot.remoteStream?.dispose();
      await slot.pc.close();
    } catch (e) {
      _log.fine('remove peer[$peerUserId] failed: $e');
    }
    final primary = _primaryRemoteStream();
    _emit(_state.copyWith(
      participantUserIds:
          _state.participantUserIds.where((id) => id != peerUserId).toList(),
      remoteStream: primary,
      clearRemoteStream: primary == null,
      remoteStreams: _remoteStreamsSnapshot(),
    ));
  }

  Future<void> _onGroupPeerHangup(pb.CallNotify notify) async {
    final actorId = _notifyPeerId(notify);
    if (!Ids.isZeroOrEmpty(actorId) && actorId != _state.peerUserId) {
      await _removePeer(actorId);
      final participants = _participantIds(notify);
      if (participants.isNotEmpty) {
        _emit(_state.copyWith(participantUserIds: participants));
      }
      return;
    }
    await _finish(CallEndReason.hangup, notifyServer: false);
  }

  MediaStream? _primaryRemoteStream() {
    for (final slot in _peers.values) {
      final stream = slot.remoteStream;
      if (stream != null) return stream;
    }
    return null;
  }

  Map<String, MediaStream> _remoteStreamsSnapshot() {
    final streams = <String, MediaStream>{};
    for (final entry in _peers.entries) {
      final stream = entry.value.remoteStream;
      if (stream != null) {
        streams[entry.key] = stream;
      }
    }
    return Map.unmodifiable(streams);
  }

  // ─── 收尾 ───────────────────────────────────────────────

  Future<void> _finish(CallEndReason reason,
      {required bool notifyServer}) async {
    if (_state.phase == CallPhase.idle) return;
    _cancelIncomingTimeout();
    if (notifyServer && _state.callId.isNotEmpty) {
      sendFrame(
        pb.Cmd.CALL_HANGUP,
        (pb.CallHangup()..callId = _state.callId).writeToBuffer(),
      );
    }
    await _teardownMedia();
    _emit(CallState(
      phase: CallPhase.ended,
      callId: _state.callId,
      peerUserId: _state.peerUserId,
      peerName: _state.peerName,
      groupId: _state.groupId,
      participantUserIds: _state.participantUserIds,
      isCaller: _state.isCaller,
      media: _state.media,
      muted: _state.muted,
      endReason: reason,
      connectedAt: _state.connectedAt,
    ));
    _endedResetTimer?.cancel();
    _endedResetTimer = Timer(const Duration(seconds: 2), () {
      _emit(const CallState()); // ended 停留 2s 供 UI 展示原因，随后回 idle
    });
  }

  Future<void> _teardownMedia() async {
    _clientCallId = '';
    final peers = Map<String, _PeerSlot>.from(_peers);
    _peers.clear();
    final stream = _localStream;
    _localStream = null;
    try {
      for (final track in stream?.getTracks() ?? const []) {
        await track.stop();
      }
      await stream?.dispose();
      final remoteStreams = <MediaStream>{};
      for (final slot in peers.values) {
        final remote = slot.remoteStream;
        if (remote != null) remoteStreams.add(remote);
        await slot.pc.close();
      }
      for (final remote in remoteStreams) {
        await remote.dispose();
      }
    } catch (e) {
      _log.fine('teardown error: $e');
    }
  }

  String _notifyPeerId(pb.CallNotify notify) {
    final peerId = Ids.toStr(notify.peerUserId);
    return Ids.isZeroOrEmpty(peerId) ? '' : peerId;
  }

  String _directPeerId(pb.CallNotify notify) {
    final notified = _notifyPeerId(notify);
    if (!Ids.isZeroOrEmpty(notified)) return notified;
    return Ids.isZeroOrEmpty(_state.peerUserId) ? 'peer' : _state.peerUserId;
  }

  List<String> _participantIds(pb.CallNotify notify) {
    return notify.participantUserIds
        .map((id) => Ids.toStr(id))
        .where((id) => !Ids.isZeroOrEmpty(id))
        .toList();
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

  pb.CallMediaType _mediaToProto(CallMedia media) => switch (media) {
        CallMedia.voice => pb.CallMediaType.CALL_MEDIA_VOICE,
        CallMedia.video => pb.CallMediaType.CALL_MEDIA_VIDEO,
      };

  CallMedia _mediaFromProto(pb.CallMediaType media) => switch (media) {
        pb.CallMediaType.CALL_MEDIA_VIDEO => CallMedia.video,
        _ => CallMedia.voice,
      };

  CallEndReason _inviteFailReason(int code) => switch (code) {
        7001 => CallEndReason.busy,
        7004 => CallEndReason.peerOffline,
        _ => CallEndReason.error,
      };

  Future<void> dispose() async {
    _cancelIncomingTimeout();
    _endedResetTimer?.cancel();
    await _teardownMedia();
    await _stateCtrl.close();
  }
}

class _PeerSlot {
  _PeerSlot(this.pc);

  final RTCPeerConnection pc;
  final List<RTCIceCandidate> pendingCandidates = [];
  bool remoteDescriptionSet = false;
  MediaStream? remoteStream;
}
