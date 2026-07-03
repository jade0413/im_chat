import 'dart:io';

import 'package:flutter_cache_manager/flutter_cache_manager.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import 'download_url_cache_service.dart';
import 'remote/rest/file_api.dart';

class MediaCacheService {
  MediaCacheService(
    this._cacheManager, {
    DownloadUrlCacheService? downloadUrlCache,
    Future<Directory> Function()? tempDirectory,
    DateTime Function()? now,
    MediaCachePolicy policy = const MediaCachePolicy(),
  })  : _downloadUrlCache = downloadUrlCache,
        _tempDirectory = tempDirectory ?? getTemporaryDirectory,
        _now = now ?? DateTime.now,
        _policy = policy;

  final BaseCacheManager _cacheManager;
  final DownloadUrlCacheService? _downloadUrlCache;
  final Future<Directory> Function() _tempDirectory;
  final DateTime Function() _now;
  final MediaCachePolicy _policy;

  static const _manualCacheDirs = [
    'lumo_voice_cache',
    'lumo_video_cache',
    'lumo_file_cache',
  ];

  Future<MediaCacheStats> stats() async {
    final files = await _collectManualFiles(await _tempDirectory());
    return MediaCacheStats(
      bytes: files.fold<int>(0, (sum, file) => sum + file.bytes),
      files: files.length,
      downloadUrlEntries: _downloadUrlCache?.entryCount ?? 0,
    );
  }

  Future<MediaCacheCleanupResult> trim({MediaCachePolicy? policy}) async {
    final effective = policy ?? _policy;
    final tempDir = await _tempDirectory();
    final now = _now();
    final files = await _collectManualFiles(tempDir);
    var deletedBytes = 0;
    var deletedFiles = 0;
    final kept = <_CacheFile>[];

    final shouldTrimByAge = effective.maxAge > Duration.zero;
    final cutoff = now.subtract(effective.maxAge);
    for (final file in files) {
      if (shouldTrimByAge && file.modified.isBefore(cutoff)) {
        final deleted = await _deleteFile(file);
        deletedBytes += deleted.bytes;
        deletedFiles += deleted.files;
      } else {
        kept.add(file);
      }
    }

    var keptBytes = kept.fold<int>(0, (sum, file) => sum + file.bytes);
    if (effective.maxBytes > 0 && keptBytes > effective.maxBytes) {
      kept.sort((a, b) => a.modified.compareTo(b.modified));
      for (final file in kept) {
        if (keptBytes <= effective.maxBytes) break;
        final deleted = await _deleteFile(file);
        deletedBytes += deleted.bytes;
        deletedFiles += deleted.files;
        keptBytes -= deleted.bytes;
      }
    }

    final clearedUrlEntries = _downloadUrlCache?.trimExpired() ?? 0;
    return MediaCacheCleanupResult(
      deletedBytes: deletedBytes,
      deletedFiles: deletedFiles,
      clearedDownloadUrlEntries: clearedUrlEntries,
    );
  }

  Future<MediaCacheCleanupResult> clearAll() async {
    var deletedBytes = 0;
    var deletedFiles = 0;

    final tempDir = await _tempDirectory();
    for (final dirName in _manualCacheDirs) {
      final dir = Directory(p.join(tempDir.path, dirName));
      final stats = await _deleteDirectory(dir);
      deletedBytes += stats.bytes;
      deletedFiles += stats.files;
    }

    await _cacheManager.emptyCache();
    final clearedUrlEntries = _downloadUrlCache?.clear() ?? 0;
    return MediaCacheCleanupResult(
      deletedBytes: deletedBytes,
      deletedFiles: deletedFiles,
      clearedDownloadUrlEntries: clearedUrlEntries,
    );
  }

  Future<MediaCacheCleanupResult> evictObjectKeys(
    Iterable<String> objectKeys,
  ) async {
    final keys = objectKeys
        .map((key) => key.trim())
        .where((key) => key.isNotEmpty)
        .toSet();
    if (keys.isEmpty) {
      return const MediaCacheCleanupResult(
        deletedBytes: 0,
        deletedFiles: 0,
      );
    }

    final removedInfos =
        _downloadUrlCache?.removeObjectKeys(keys) ?? const <FileDownloadInfo>[];
    final expandedKeys = <String>{
      ...keys,
      ...removedInfos
          .map((info) => info.objectKey.trim())
          .where((key) => key.isNotEmpty),
    };

    for (final key in expandedKeys) {
      try {
        await _cacheManager.removeFile('im-media:$key');
      } catch (_) {
        // CacheManager 清理失败不影响本地消息删除；下次访问会按需重新下载。
      }
    }

    final safeKeys = expandedKeys.map(_safeCacheKey).toSet();
    final files = await _collectManualFiles(await _tempDirectory());
    var deletedBytes = 0;
    var deletedFiles = 0;
    for (final file in files) {
      if (!_matchesObjectKey(file.file, safeKeys)) continue;
      final deleted = await _deleteFile(file);
      deletedBytes += deleted.bytes;
      deletedFiles += deleted.files;
    }

    return MediaCacheCleanupResult(
      deletedBytes: deletedBytes,
      deletedFiles: deletedFiles,
      clearedDownloadUrlEntries: removedInfos.length,
    );
  }

  Future<List<_CacheFile>> _collectManualFiles(Directory tempDir) async {
    final result = <_CacheFile>[];
    for (final dirName in _manualCacheDirs) {
      final dir = Directory(p.join(tempDir.path, dirName));
      if (!await dir.exists()) continue;
      await for (final entity
          in dir.list(recursive: true, followLinks: false)) {
        if (entity is! File) continue;
        try {
          final stat = await entity.stat();
          result.add(_CacheFile(
            entity,
            stat.size,
            stat.modified,
          ));
        } catch (_) {
          // 单个文件正在被系统占用时跳过，本轮清理不因为它失败。
        }
      }
    }
    return result;
  }

  Future<_CacheDirStats> _deleteFile(_CacheFile file) async {
    try {
      await file.file.delete();
      return _CacheDirStats(bytes: file.bytes, files: 1);
    } catch (_) {
      return const _CacheDirStats();
    }
  }

  Future<_CacheDirStats> _deleteDirectory(Directory dir) async {
    if (!await dir.exists()) return const _CacheDirStats();
    var bytes = 0;
    var files = 0;
    await for (final entity in dir.list(recursive: true, followLinks: false)) {
      if (entity is File) {
        files++;
        try {
          bytes += await entity.length();
        } catch (_) {
          // 清理缓存不因单个文件状态异常失败；后续 delete 会尽力处理。
        }
      }
    }
    await dir.delete(recursive: true);
    return _CacheDirStats(bytes: bytes, files: files);
  }

  String _safeCacheKey(String objectKey) =>
      objectKey.replaceAll(RegExp(r'[^a-zA-Z0-9._-]+'), '_');

  bool _matchesObjectKey(File file, Set<String> safeKeys) {
    final name = p.basename(file.path);
    for (final key in safeKeys) {
      if (key.isEmpty) continue;
      if (name == key || name.startsWith('$key.')) return true;
    }
    return false;
  }
}

class MediaCachePolicy {
  const MediaCachePolicy({
    this.maxAge = const Duration(days: 30),
    this.maxBytes = 512 * 1024 * 1024,
  });

  final Duration maxAge;
  final int maxBytes;
}

class MediaCacheStats {
  const MediaCacheStats({
    required this.bytes,
    required this.files,
    this.downloadUrlEntries = 0,
  });

  final int bytes;
  final int files;
  final int downloadUrlEntries;

  String get displaySize => _formatBytes(bytes);
}

class MediaCacheCleanupResult {
  const MediaCacheCleanupResult({
    required this.deletedBytes,
    required this.deletedFiles,
    this.clearedDownloadUrlEntries = 0,
  });

  final int deletedBytes;
  final int deletedFiles;
  final int clearedDownloadUrlEntries;

  String get displaySize => _formatBytes(deletedBytes);
}

String _formatBytes(int bytes) {
  if (bytes < 1024) return '$bytes B';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
  return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
}

class _CacheFile {
  const _CacheFile(this.file, this.bytes, this.modified);

  final File file;
  final int bytes;
  final DateTime modified;
}

class _CacheDirStats {
  const _CacheDirStats({this.bytes = 0, this.files = 0});

  final int bytes;
  final int files;
}
