import 'dart:typed_data';

import '../../proto/codec.dart' as pb;
import 'im_packet.dart';

/// WS Frame 编解码门面。底层委托 `core/proto/codec.dart`，避免业务层直接依赖
/// protobuf 生成路径。
class PacketCodec {
  const PacketCodec();

  Uint8List encode(ImPacket packet) {
    return pb.encodeFrame(
      packet.cmd,
      body: packet.body,
      reqIdOverride: packet.reqId,
    );
  }

  pb.Frame decode(Uint8List data) => pb.decodeFrame(data);
}
