import 'dart:convert';
import 'dart:io';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

abstract class CredentialStorage {
  Future<String?> read({required String key});

  Future<void> write({required String key, required String value});

  Future<void> delete({required String key});
}

class SecureCredentialStorage implements CredentialStorage {
  const SecureCredentialStorage(this._storage);

  final FlutterSecureStorage _storage;

  @override
  Future<String?> read({required String key}) => _storage.read(key: key);

  @override
  Future<void> write({required String key, required String value}) =>
      _storage.write(key: key, value: value);

  @override
  Future<void> delete({required String key}) => _storage.delete(key: key);
}

class LocalCredentialStorage implements CredentialStorage {
  const LocalCredentialStorage({this.fileName = 'debug_credentials.json'});

  final String fileName;

  Future<File> _file() async {
    final dir = await getApplicationSupportDirectory();
    return File(p.join(dir.path, fileName));
  }

  Future<Map<String, String>> _readAll() async {
    final file = await _file();
    if (!await file.exists()) return {};

    try {
      final text = await file.readAsString();
      if (text.trim().isEmpty) return {};

      final decoded = jsonDecode(text);
      if (decoded is! Map) return {};

      final values = <String, String>{};
      for (final entry in decoded.entries) {
        if (entry.key is String && entry.value is String) {
          values[entry.key as String] = entry.value as String;
        }
      }
      return values;
    } on FormatException {
      return {};
    } on FileSystemException {
      return {};
    }
  }

  Future<void> _writeAll(Map<String, String> values) async {
    final file = await _file();
    await file.parent.create(recursive: true);

    final tmp = File('${file.path}.tmp');
    await tmp.writeAsString(jsonEncode(values));

    if (await file.exists()) {
      await file.delete();
    }
    await tmp.rename(file.path);
  }

  @override
  Future<String?> read({required String key}) async {
    final values = await _readAll();
    return values[key];
  }

  @override
  Future<void> write({required String key, required String value}) async {
    final values = await _readAll();
    values[key] = value;
    await _writeAll(values);
  }

  @override
  Future<void> delete({required String key}) async {
    final values = await _readAll();
    if (values.remove(key) == null) return;
    await _writeAll(values);
  }
}
