import 'dart:typed_data';

import '../../core/proto/codec.dart' as pb;
import 'ws/im_socket.dart';

/// IM 网关远程数据源门面。
///
/// 当前 WebSocket 生命周期由 `ImEngine` 直接协调；这个类预留给后续把网关发送、
/// ACK、SYNC 封装成更明确的 Remote DataSource。
class ImGatewayApi {
  const ImGatewayApi(this._socket);

  final ImSocket _socket;

  bool sendPacket(pb.Cmd cmd, Uint8List body) => _socket.send(cmd, body);
  Future<void> reconnect() => _socket.connect();
  Future<void> disconnect() => _socket.disconnect();
}
