import 'dart:async';
import 'dart:typed_data';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:fixnum/fixnum.dart';

import '../../../core/config/env.dart';
import '../../../core/logging.dart';
import '../../../core/platform/platform_info.dart';
import '../../../core/proto/codec.dart' as pb;
import '../../models/enums.dart';
import '../token_refresh.dart';
import 'reconnect.dart';
import 'ws_channel.dart';

/// 连接层依赖（注入，保持 ImSocket 与 DB/UI 解耦）。
class ImSocketDelegate {
  ImSocketDelegate({
    required this.getAccessToken,
    required this.getDeviceId,
    required this.refreshToken,
    required this.onAuthExpired,
    required this.onKicked,
    required this.onBusinessFrame,
    required this.buildSyncReqBody,
    required this.onAuthenticated,
  });

  final Future<String?> Function() getAccessToken;
  final Future<String> Function() getDeviceId;

  /// AUTH 失败时刷新 token，返回三态（成功/失效/网络错误）。
  final Future<TokenRefreshResult> Function() refreshToken;

  /// 凭证真失效 / 被踢——上层登出。
  final Future<void> Function() onAuthExpired;
  final void Function(pb.KickNotify kick) onKicked;

  /// 业务帧分发（MSG_PUSH / MSG_SEND_ACK / SYNC_RESP / ...）。
  final void Function(pb.Frame frame) onBusinessFrame;

  /// 由 DB 构造 SYNC_REQ body。
  final Future<Uint8List> Function() buildSyncReqBody;

  /// AUTH_ACK 成功——引擎据此 drain Outbox（断网期堆积的消息恢复发送）。
  final void Function(int userId) onAuthenticated;
}

/// WebSocket 连接状态机（**自有设计**，非直接照搬 im-web）。
///
/// 改进点（针对 im-web 重连缺陷）：
/// 1. **鉴权恢复三态**：刷新 token 区分「凭证失效→登出」与「网络错误→退避重试」，
///    网络抖动不再误登出（im-web 用 bool 把两者混为一谈）。
/// 2. **失败计数封顶**：连续 AUTH 失败超 2 次才登出，避免双端互踢瞬时抖动误杀；
///    AUTH 成功即清零。
/// 3. **并发连接守卫**：`_connecting` + generation 双保险，杜绝重复 socket。
/// 4. **退避+抖动+封顶**：1→2→4…→60s ±30%，连接成功/网络恢复/前台唤醒即重置。
/// 5. **半死链探测**：2.5 个心跳周期无下行帧 → 主动断开重连（兜底 TCP 半开）。
/// 6. **发送解耦**：不再内存暂存待发消息；持久化 Outbox 由 ImEngine 在
///    `onAuthenticated` 时统一 drain，断网/重启都能恢复（验收要求 #8）。
class ImSocket {
  ImSocket(this.delegate);

  final ImSocketDelegate delegate;
  final _log = appLogger('ws');

  static const int _maxAuthFailStreak = 2;

  WebSocketChannelLike? _channel;
  StreamSubscription<dynamic>? _sub;
  StreamSubscription<List<ConnectivityResult>>? _connSub;

  Timer? _reconnectTimer;
  Timer? _heartbeatTimer;
  Timer? _openTimer;
  Timer? _authAckTimer;

  bool _manualClose = false;
  bool _connecting = false;
  int _generation = 0;
  bool _hasAuthAck = false;
  int _authFailStreak = 0;
  int _lastFrameAt = 0;
  int _heartbeatMs = 30000;
  bool _netListenersBound = false;
  // TODO(im-app): Remote Config 接入后允许按环境/租户下发心跳、退避和
  // AUTH_ACK 超时参数，避免发版才能调整连接策略。

  final ReconnectBackoff _backoff = ReconnectBackoff();

  final StreamController<ConnectionState> _stateCtrl =
      StreamController.broadcast();
  Stream<ConnectionState> get stateStream => _stateCtrl.stream;
  ConnectionState _state = ConnectionState.idle;
  ConnectionState get state => _state;

  void _setState(ConnectionState s, {String? detail}) {
    _state = s;
    if (!_stateCtrl.isClosed) _stateCtrl.add(s);
    if (detail != null) _log.fine('state=$s $detail');
  }

  // ─── 连接控制 ───────────────────────────────────────────

  Future<void> connect() async {
    if (_connecting) {
      _log.fine('connect ignored: already connecting');
      return;
    }
    _bindNetworkListeners();
    final token = await delegate.getAccessToken();
    if (token == null || token.isEmpty) {
      _log.info('connect aborted: no token');
      return;
    }
    _connecting = true;
    final generation = ++_generation;
    _manualClose = false;
    _hasAuthAck = false;
    _lastFrameAt = 0;
    _clearReconnectTimer();
    await _closeChannel();
    _setState(ConnectionState.connecting, detail: 'connecting ${Env.wsUrl}');

    final WebSocketChannelLike channel;
    try {
      channel = await WebSocketChannelLike.connect(Env.wsUrl);
    } catch (e) {
      _log.warning('open failed: $e');
      _connecting = false;
      if (_isActive(generation) && !_manualClose) _scheduleReconnect();
      return;
    }
    if (!_isActive(generation)) {
      await channel.close();
      _connecting = false;
      return;
    }
    _channel = channel;

    _sub = channel.stream.listen(
      (data) => _onData(data, generation),
      onError: (Object e) => _onError(e, generation),
      onDone: () => _onDone(generation),
      cancelOnError: true,
    );

    // 发 AUTH，进入鉴权等待
    final deviceId = await delegate.getDeviceId();
    final auth = pb.AuthReq()
      ..token = token
      ..tenantId = Int64(Env.tenantId)
      ..deviceId = deviceId
      ..platform = PlatformInfo.current.protoValue
      ..appVersion = Env.appVersion
      ..timestamp = Int64(DateTime.now().millisecondsSinceEpoch ~/ 1000);
    _setState(ConnectionState.authenticating, detail: 'AUTH sent');
    _startAuthAckTimer(generation);
    _rawSend(pb.Cmd.AUTH, auth.writeToBuffer());
  }

  Future<void> disconnect({bool manual = true}) async {
    _generation++;
    _manualClose = manual;
    _connecting = false;
    _clearReconnectTimer();
    _stopOpenTimer();
    _stopAuthAckTimer();
    _stopHeartbeat();
    await _closeChannel();
    _setState(ConnectionState.closed, detail: 'disconnect manual=$manual');
  }

  /// 前台唤醒钩子（App 由后台回前台时调用）：立即探活/重连。
  void onAppResumed() => unawaited(_reconnectNow('app resumed'));

  // ─── 收帧 ───────────────────────────────────────────────

  void _onData(dynamic data, int generation) {
    if (!_isActive(generation)) return;
    if (data is! List<int>) return;
    pb.Frame frame;
    try {
      frame = pb.decodeFrame(Uint8List.fromList(data));
    } catch (e) {
      _log.warning('frame decode failed: $e');
      return;
    }
    _lastFrameAt = DateTime.now().millisecondsSinceEpoch;
    switch (frame.cmd) {
      case pb.Cmd.AUTH_ACK:
        _handleAuthAck(frame.body);
      case pb.Cmd.PONG:
        if (_state != ConnectionState.connected) {
          _setState(ConnectionState.connected);
        }
      case pb.Cmd.PING:
        _rawSend(pb.Cmd.PONG, null);
      case pb.Cmd.KICK:
        _handleKick(frame.body);
      default:
        delegate.onBusinessFrame(frame);
    }
  }

  void _handleAuthAck(List<int> body) {
    _hasAuthAck = true;
    _stopAuthAckTimer();
    final ack = pb.AuthResp.fromBuffer(body);
    if (ack.code != 0) {
      _log.warning('AUTH rejected code=${ack.code} msg=${ack.message}');
      unawaited(_recoverAuth(ack.message));
      return;
    }
    // 成功：清零失败计数与退避，启动心跳，触发同步与 Outbox drain
    _authFailStreak = 0;
    _backoff.reset();
    _connecting = false;
    _heartbeatMs =
        (ack.heartbeatIntervalSec == 0 ? 30 : ack.heartbeatIntervalSec) * 1000;
    _setState(ConnectionState.connected,
        detail: 'authenticated u=${ack.userId}');
    _startHeartbeat();
    delegate.onAuthenticated(ack.userId.toInt());
    unawaited(sendSyncReq()); // 重连必发增量同步补齐离线消息
  }

  /// 鉴权恢复（自有设计核心）：刷新 token 后按三态决策。
  Future<void> _recoverAuth(String reason) async {
    _authFailStreak++;
    _connecting = false;
    await _closeChannel(); // 取消订阅，避免 onDone 触发并发重连

    if (_authFailStreak > _maxAuthFailStreak) {
      _log.warning('auth fail streak exceeded → logout');
      await delegate.onAuthExpired();
      _setState(ConnectionState.closed);
      return;
    }

    _setState(ConnectionState.reconnecting, detail: 'refreshing token');
    final result = await delegate.refreshToken();
    switch (result) {
      case TokenRefreshResult.success:
        _manualClose = false;
        await connect(); // 用新 token 重连
      case TokenRefreshResult.authInvalid:
        await delegate.onAuthExpired();
        _setState(ConnectionState.closed);
      case TokenRefreshResult.networkError:
        // 网络问题不登出——退避后重试，且不消耗「真失效」判定
        _authFailStreak--;
        _manualClose = false;
        _scheduleReconnect();
    }
  }

  void _handleKick(List<int> body) {
    final kick = pb.KickNotify.fromBuffer(body);
    _log.info('KICK reason=${kick.reason}');
    delegate.onKicked(kick);
    unawaited(disconnect(manual: true)); // 被踢=有意断开，不重连
  }

  void _onError(Object e, int generation) {
    if (!_isActive(generation)) return;
    _log.warning('socket error: $e');
    if (!_hasAuthAck) _setState(ConnectionState.error, detail: '$e');
  }

  void _onDone(int generation) {
    if (!_isActive(generation)) return;
    _stopOpenTimer();
    _stopAuthAckTimer();
    _stopHeartbeat();
    _channel = null;
    _connecting = false;
    if (_manualClose) {
      _setState(ConnectionState.closed, detail: 'closed (manual)');
      return;
    }
    _scheduleReconnect();
  }

  // ─── 心跳 / 存活探测 ────────────────────────────────────

  void _startHeartbeat() {
    _stopHeartbeat();
    _lastFrameAt = DateTime.now().millisecondsSinceEpoch;
    _heartbeatTimer = Timer.periodic(Duration(milliseconds: _heartbeatMs), (_) {
      final now = DateTime.now().millisecondsSinceEpoch;
      if (_lastFrameAt > 0 && now - _lastFrameAt > _heartbeatMs * 2.5) {
        _log.warning('liveness timeout → force reconnect');
        _forceReconnect();
        return;
      }
      _rawSend(pb.Cmd.PING, null);
    });
  }

  void _forceReconnect() {
    _stopHeartbeat();
    unawaited(_closeChannel());
    if (!_manualClose) _scheduleReconnect();
  }

  // ─── 网络/前台恢复 → 即时重连 ───────────────────────────
  void _bindNetworkListeners() {
    if (_netListenersBound) return;
    _netListenersBound = true;
    _connSub = Connectivity().onConnectivityChanged.listen((results) {
      final online = results.any((r) => r != ConnectivityResult.none);
      if (online) unawaited(_reconnectNow('network online'));
    });
  }

  Future<void> _reconnectNow(String reason) async {
    if (_manualClose) return;
    final token = await delegate.getAccessToken();
    if (token == null || token.isEmpty) return;
    if (_state == ConnectionState.connected && (_channel?.isOpen ?? false)) {
      _rawSend(pb.Cmd.PING, null); // 已连：探活，半死链交给看门狗
      return;
    }
    if (_connecting) return;
    _log.info('immediate reconnect: $reason');
    _clearReconnectTimer();
    _backoff.reset();
    await connect();
  }

  // ─── 发送（对外统一入口）────────────────────────────────

  /// 发送一帧业务消息。返回是否真正写出（false=连接未就绪，调用方据此置 pending）。
  bool send(pb.Cmd cmd, Uint8List body, {Int64? reqId}) =>
      _rawSend(cmd, body, reqId: reqId);

  Future<void> sendSyncReq() async {
    final body = await delegate.buildSyncReqBody();
    _rawSend(pb.Cmd.SYNC_REQ, body);
  }

  bool _rawSend(pb.Cmd cmd, Uint8List? body, {Int64? reqId}) {
    final ch = _channel;
    if (ch == null || !ch.isOpen) {
      _log.fine('send skipped cmd=${cmd.name} (not open)');
      return false;
    }
    ch.add(pb.encodeFrame(cmd, body: body, reqIdOverride: reqId));
    return true;
  }

  // ─── 定时器 ─────────────────────────────────────────────

  void _scheduleReconnect() {
    if (_reconnectTimer != null) return;
    _setState(ConnectionState.reconnecting);
    final delay = _backoff.nextDelay();
    _log.fine('reconnect in ${delay}ms');
    _reconnectTimer = Timer(Duration(milliseconds: delay), () {
      _reconnectTimer = null;
      unawaited(connect());
    });
  }

  void _clearReconnectTimer() {
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
  }

  void _stopHeartbeat() {
    _heartbeatTimer?.cancel();
    _heartbeatTimer = null;
  }

  void _startAuthAckTimer(int generation) {
    _stopAuthAckTimer();
    _authAckTimer = Timer(const Duration(seconds: 8), () {
      if (!_isActive(generation) || _hasAuthAck) return;
      _log.warning('AUTH_ACK timeout');
      _setState(ConnectionState.error, detail: 'AUTH_ACK timeout');
      _connecting = false;
      unawaited(_closeChannelThenReconnect());
    });
  }

  Future<void> _closeChannelThenReconnect() async {
    await _closeChannel();
    if (!_manualClose) _scheduleReconnect();
  }

  void _stopOpenTimer() {
    _openTimer?.cancel();
    _openTimer = null;
  }

  void _stopAuthAckTimer() {
    _authAckTimer?.cancel();
    _authAckTimer = null;
  }

  Future<void> _closeChannel() async {
    await _sub?.cancel();
    _sub = null;
    final ch = _channel;
    _channel = null;
    if (ch != null) await ch.close();
  }

  bool _isActive(int generation) => _generation == generation;

  Future<void> dispose() async {
    await disconnect(manual: true);
    await _connSub?.cancel();
    await _stateCtrl.close();
  }
}
