import 'dart:io';

import 'package:flutter_cache_manager/flutter_cache_manager.dart';
import 'package:im_app/data/download_url_cache_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:im_app/data/media_cache_service.dart';
import 'package:im_app/data/remote/rest/file_api.dart';
import 'package:path/path.dart' as p;

void main() {
  late Directory tempDir;
  late _FakeCacheManager cacheManager;
  late DateTime now;
  late DownloadUrlCacheService urlCache;

  setUp(() async {
    tempDir = await Directory.systemTemp.createTemp('lumo_media_cache_test_');
    cacheManager = _FakeCacheManager();
    now = DateTime.utc(2026, 7, 3);
    urlCache = DownloadUrlCacheService.withResolver(
      (objectKey, {variant}) async => FileDownloadInfo(
        objectKey: objectKey,
        url: 'https://cdn.example.com/$objectKey',
        expiresAt: now.add(const Duration(minutes: 10)).millisecondsSinceEpoch,
        transformed: false,
      ),
      now: () => now,
      refreshSkew: const Duration(minutes: 1),
    );
  });

  tearDown(() async {
    if (await tempDir.exists()) {
      await tempDir.delete(recursive: true);
    }
  });

  MediaCacheService service({
    DateTime? now,
    MediaCachePolicy policy = const MediaCachePolicy(),
  }) {
    return MediaCacheService(
      cacheManager,
      downloadUrlCache: urlCache,
      tempDirectory: () async => tempDir,
      now: () => now ?? DateTime.utc(2026, 7, 3),
      policy: policy,
    );
  }

  test('stats reports manual cache bytes and files', () async {
    await _writeCacheFile(
      tempDir,
      'lumo_voice_cache',
      'a.voice',
      bytes: 10,
      modified: DateTime.utc(2026, 7, 2),
    );
    await _writeCacheFile(
      tempDir,
      'lumo_video_cache',
      'b.video',
      bytes: 15,
      modified: DateTime.utc(2026, 7, 2),
    );

    final stats = await service().stats();

    expect(stats.files, 2);
    expect(stats.bytes, 25);
    expect(stats.displaySize, '25 B');
    expect(stats.downloadUrlEntries, 0);
  });

  test('stats reports download url cache entries', () async {
    await urlCache.resolve('1/202607/a.png');
    await urlCache.resolve('1/202607/b.png');

    final stats = await service().stats();

    expect(stats.files, 0);
    expect(stats.downloadUrlEntries, 2);
  });

  test('trim removes files older than maxAge and keeps fresh files', () async {
    final oldFile = await _writeCacheFile(
      tempDir,
      'lumo_voice_cache',
      'old.voice',
      bytes: 10,
      modified: DateTime.utc(2026, 5, 1),
    );
    final freshFile = await _writeCacheFile(
      tempDir,
      'lumo_voice_cache',
      'fresh.voice',
      bytes: 20,
      modified: DateTime.utc(2026, 7, 2),
    );

    final result = await service(
      now: DateTime.utc(2026, 7, 3),
      policy: const MediaCachePolicy(maxAge: Duration(days: 30), maxBytes: 0),
    ).trim();

    expect(result.deletedFiles, 1);
    expect(result.deletedBytes, 10);
    expect(result.clearedDownloadUrlEntries, 0);
    expect(await oldFile.exists(), isFalse);
    expect(await freshFile.exists(), isTrue);
  });

  test('trim also removes stale download url cache entries', () async {
    await urlCache.resolve('1/202607/a.png');
    await urlCache.resolve('1/202607/b.png');
    now = now.add(const Duration(minutes: 9, seconds: 30));

    final result = await service(now: now).trim();

    expect(result.deletedFiles, 0);
    expect(result.clearedDownloadUrlEntries, 2);
    expect(urlCache.entryCount, 0);
  });

  test('trim enforces maxBytes by deleting oldest files first', () async {
    final oldest = await _writeCacheFile(
      tempDir,
      'lumo_file_cache',
      'oldest.bin',
      bytes: 4,
      modified: DateTime.utc(2026, 7, 1),
    );
    final newest = await _writeCacheFile(
      tempDir,
      'lumo_file_cache',
      'newest.bin',
      bytes: 4,
      modified: DateTime.utc(2026, 7, 2),
    );

    final result = await service(
      policy: const MediaCachePolicy(maxAge: Duration.zero, maxBytes: 5),
    ).trim();

    expect(result.deletedFiles, 1);
    expect(result.deletedBytes, 4);
    expect(await oldest.exists(), isFalse);
    expect(await newest.exists(), isTrue);
  });

  test('clearAll removes manual dirs and empties cache manager', () async {
    await _writeCacheFile(
      tempDir,
      'lumo_voice_cache',
      'voice.cache',
      bytes: 10,
      modified: DateTime.utc(2026, 7, 2),
    );
    await _writeCacheFile(
      tempDir,
      'lumo_video_cache',
      'video.cache',
      bytes: 20,
      modified: DateTime.utc(2026, 7, 2),
    );

    final result = await service().clearAll();

    expect(result.deletedFiles, 2);
    expect(result.deletedBytes, 30);
    expect(result.clearedDownloadUrlEntries, 0);
    expect(cacheManager.emptied, isTrue);
    expect(
      await Directory(p.join(tempDir.path, 'lumo_voice_cache')).exists(),
      isFalse,
    );
    expect(
      await Directory(p.join(tempDir.path, 'lumo_video_cache')).exists(),
      isFalse,
    );
  });

  test('clearAll also clears download url cache entries', () async {
    await urlCache.resolve('1/202607/a.png');
    await urlCache.resolve('1/202607/b.png');

    final result = await service().clearAll();

    expect(result.clearedDownloadUrlEntries, 2);
    expect(urlCache.entryCount, 0);
  });

  test('evictObjectKeys removes matching manual caches and url entries',
      () async {
    urlCache = DownloadUrlCacheService.withResolver(
      (objectKey, {variant}) async => FileDownloadInfo(
        objectKey:
            variant == 'playback' ? '1/202607/transcoded/a.mp4' : objectKey,
        url: 'https://cdn.example.com/$objectKey/${variant ?? 'original'}',
        expiresAt: now.add(const Duration(minutes: 10)).millisecondsSinceEpoch,
        transformed: variant == 'playback',
      ),
      now: () => now,
      refreshSkew: const Duration(minutes: 1),
    );
    await urlCache.resolve('1/202607/a.mov', variant: 'playback');
    final voiceFile = await _writeCacheFile(
      tempDir,
      'lumo_voice_cache',
      '1_202607_voice.m4a',
      bytes: 10,
      modified: DateTime.utc(2026, 7, 2),
    );
    final transformedVideo = await _writeCacheFile(
      tempDir,
      'lumo_video_cache',
      '1_202607_transcoded_a.mp4',
      bytes: 20,
      modified: DateTime.utc(2026, 7, 2),
    );
    final unrelated = await _writeCacheFile(
      tempDir,
      'lumo_video_cache',
      '1_202607_other.mp4',
      bytes: 30,
      modified: DateTime.utc(2026, 7, 2),
    );

    final result = await service().evictObjectKeys([
      '1/202607/a.mov',
      '1/202607/voice',
      '1/202607/thumb.jpg',
    ]);

    expect(result.deletedFiles, 2);
    expect(result.deletedBytes, 30);
    expect(result.clearedDownloadUrlEntries, 1);
    expect(await voiceFile.exists(), isFalse);
    expect(await transformedVideo.exists(), isFalse);
    expect(await unrelated.exists(), isTrue);
    expect(urlCache.entryCount, 0);
    expect(cacheManager.removedKeys, contains('im-media:1/202607/a.mov'));
    expect(
      cacheManager.removedKeys,
      contains('im-media:1/202607/transcoded/a.mp4'),
    );
    expect(cacheManager.removedKeys, contains('im-media:1/202607/thumb.jpg'));
  });
}

Future<File> _writeCacheFile(
  Directory root,
  String dirName,
  String name, {
  required int bytes,
  required DateTime modified,
}) async {
  final dir = Directory(p.join(root.path, dirName));
  await dir.create(recursive: true);
  final file = File(p.join(dir.path, name));
  await file.writeAsBytes(List<int>.filled(bytes, 1), flush: true);
  await file.setLastModified(modified);
  return file;
}

class _FakeCacheManager implements BaseCacheManager {
  var emptied = false;
  final removedKeys = <String>[];

  @override
  Future<void> emptyCache() async {
    emptied = true;
  }

  @override
  Future<void> removeFile(String key) async {
    removedKeys.add(key);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}
