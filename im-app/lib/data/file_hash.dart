import 'package:crypto/crypto.dart' as crypto;
import 'package:flutter/foundation.dart';

const defaultHashIsolateThresholdBytes = 1024 * 1024;

Future<String> sha256Hex(
  Uint8List bytes, {
  int isolateThresholdBytes = defaultHashIsolateThresholdBytes,
}) {
  if (bytes.lengthInBytes < isolateThresholdBytes) {
    return Future.value(sha256HexSync(bytes));
  }
  return compute(sha256HexSync, bytes);
}

String sha256HexSync(Uint8List bytes) => crypto.sha256.convert(bytes).toString();
