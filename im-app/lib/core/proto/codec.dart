// ============================================================
// WS 帧编解码（移植 im-web src/proto/codec.ts）。
//
// 依赖 protoc 生成物：运行 `bash tool/generate_proto.sh` 后，
// lib/core/proto/generated/ 下会出现 ws/ body/ common/ 的 *.pb.dart。
// 生成前本文件的 export 会报红，属正常（与 im-web 必须先 `proto:gen` 一致）。
//
// 传输：1 个 WebSocket Binary Message = 1 个 Frame（不另加长度前缀，见 frame.proto）。
// ============================================================
import 'dart:typed_data';
import 'package:fixnum/fixnum.dart';

// 单一导入边界：全 App 通过本文件拿 proto 类型，不直接 import generated/。
export 'generated/ws/frame.pb.dart';
export 'generated/body/messages.pb.dart';
export 'generated/common/content.pb.dart';
export 'generated/common/enums.pb.dart';
export 'generated/common/error.pb.dart';

import 'generated/ws/frame.pb.dart';

/// 连接内自增 req_id（req/ack 配对）。服务端主动推送 req_id=0。
int _reqId = 0;

Int64 nextReqId() {
  _reqId = _reqId >= 0x7FFFFFFFFFFFFF ? 1 : _reqId + 1;
  return Int64(_reqId);
}

/// 编码一帧。reqIdOverride 用于 MSG_RECV_ACK 回带网关分配的 req_id（D28）。
Uint8List encodeFrame(Cmd cmd, {List<int>? body, Int64? reqIdOverride}) {
  final frame = Frame()
    ..version = 1
    ..reqId = reqIdOverride ?? nextReqId()
    ..cmd = cmd
    ..body = body ?? Uint8List(0);
  return frame.writeToBuffer();
}

Frame decodeFrame(Uint8List data) => Frame.fromBuffer(data);

/// 调试用 Cmd 标签。
String cmdLabel(Cmd cmd) => '${cmd.name}(${cmd.value})';
