import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:im_app/data/file_hash.dart';

void main() {
  test('sha256Hex returns expected digest for small payload', () async {
    final bytes = Uint8List.fromList(utf8.encode('hello'));

    final digest = await sha256Hex(bytes);

    expect(
      digest,
      '2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824',
    );
  });

  test('sha256Hex can run through compute isolate path', () async {
    final bytes = Uint8List.fromList(List<int>.generate(4096, (i) => i % 251));

    final digest = await sha256Hex(bytes, isolateThresholdBytes: 1);

    expect(digest, sha256HexSync(bytes));
  });
}
