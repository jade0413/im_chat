import 'dart:typed_data';

import 'package:web_socket_channel/web_socket_channel.dart';
import 'package:web_socket_channel/status.dart' as ws_status;

/// 对 web_socket_channel 的薄封装：四端一致（Android/iOS/macOS/Windows 走 dart:io 实现），
/// 暴露 ImSocket 需要的最小接口（连接就绪 / 二进制收发 / 关闭 / 是否打开）。
class WebSocketChannelLike {
  WebSocketChannelLike._(this._channel);

  final WebSocketChannel _channel;

  /// 建立连接并等待握手完成（失败抛异常，由调用方走重连）。
  static Future<WebSocketChannelLike> connect(String url) async {
    final channel = WebSocketChannel.connect(Uri.parse(url));
    await channel.ready; // 抛 WebSocketChannelException / SocketException
    return WebSocketChannelLike._(channel);
  }

  Stream<dynamic> get stream => _channel.stream;

  /// closeCode 为 null 表示仍打开。
  bool get isOpen => _channel.closeCode == null;

  /// 发送二进制帧（1 个 Binary Message = 1 个 Frame）。
  void add(Uint8List bytes) => _channel.sink.add(bytes);

  Future<void> close() async {
    try {
      await _channel.sink.close(ws_status.normalClosure);
    } catch (_) {
      // 关闭竞态忽略
    }
  }
}
