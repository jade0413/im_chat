import 'package:uuid/uuid.dart';

const _uuid = Uuid();

/// 客户端生成 client_msg_id（D9：双 ID，客户端 UUID 做幂等去重）。
String createUuid() => _uuid.v4();
