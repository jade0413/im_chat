import 'dart:typed_data';

import 'package:fixnum/fixnum.dart';

import '../../proto/codec.dart' as pb;
import 'protocol_version.dart';

/// 应用层协议包门面。
///
/// 传输事实来源仍是 im-proto 的 `Frame`。这个类型用于隔离 UI/Repository 和
/// protobuf 生成物，后续可以承载调试信息、链路耗时、trace id。
class ImPacket {
  const ImPacket({
    required this.cmd,
    required this.body,
    this.version = protocolVersion,
    this.reqId,
  });

  final pb.Cmd cmd;
  final Uint8List body;
  final int version;
  final Int64? reqId;
}
