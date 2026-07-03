import 'package:flutter_test/flutter_test.dart';
import 'package:im_app/data/download_url_cache_service.dart';
import 'package:im_app/data/remote/rest/file_api.dart';

void main() {
  test('reuses download info before refresh window', () async {
    final now = DateTime.utc(2026, 7, 3, 1);
    var calls = 0;
    final service = DownloadUrlCacheService.withResolver(
      (objectKey, {variant}) async {
        calls++;
        return _info(
          objectKey: objectKey,
          url: 'https://cdn.example.com/$calls',
          expiresAt: now.add(const Duration(minutes: 10)),
        );
      },
      now: () => now,
    );

    final first = await service.resolve('1/202607/a.png');
    final second = await service.resolve('1/202607/a.png');

    expect(first.url, 'https://cdn.example.com/1');
    expect(second.url, first.url);
    expect(calls, 1);
  });

  test('refreshes download info near expiry', () async {
    var now = DateTime.utc(2026, 7, 3, 1);
    var calls = 0;
    final service = DownloadUrlCacheService.withResolver(
      (objectKey, {variant}) async {
        calls++;
        return _info(
          objectKey: objectKey,
          url: 'https://cdn.example.com/$calls',
          expiresAt: now.add(const Duration(minutes: 10)),
        );
      },
      now: () => now,
      refreshSkew: const Duration(minutes: 1),
    );

    final first = await service.url('1/202607/a.png');
    now = now.add(const Duration(minutes: 9, seconds: 10));
    final second = await service.url('1/202607/a.png');

    expect(first, 'https://cdn.example.com/1');
    expect(second, 'https://cdn.example.com/2');
    expect(calls, 2);
  });

  test('separates cache entries by variant', () async {
    final now = DateTime.utc(2026, 7, 3, 1);
    final calls = <String>[];
    final service = DownloadUrlCacheService.withResolver(
      (objectKey, {variant}) async {
        final suffix = variant ?? 'original';
        calls.add('$objectKey#$suffix');
        return _info(
          objectKey:
              suffix == 'playback' ? '1/202607/transcoded/a.mp4' : objectKey,
          url: 'https://cdn.example.com/$suffix',
          expiresAt: now.add(const Duration(minutes: 10)),
          transformed: suffix == 'playback',
        );
      },
      now: () => now,
    );

    final original = await service.resolve('1/202607/a.mov');
    final playback =
        await service.resolve('1/202607/a.mov', variant: 'playback');
    final playbackAgain =
        await service.resolve('1/202607/a.mov', variant: 'playback');

    expect(original.url, 'https://cdn.example.com/original');
    expect(playback.url, 'https://cdn.example.com/playback');
    expect(playback.objectKey, '1/202607/transcoded/a.mp4');
    expect(playbackAgain.url, playback.url);
    expect(calls, ['1/202607/a.mov#original', '1/202607/a.mov#playback']);
  });

  test('trimExpired removes entries that need refresh soon', () async {
    var now = DateTime.utc(2026, 7, 3, 1);
    final service = DownloadUrlCacheService.withResolver(
      (objectKey, {variant}) async => _info(
        objectKey: objectKey,
        url: 'https://cdn.example.com/$objectKey',
        expiresAt: now.add(const Duration(minutes: 10)),
      ),
      now: () => now,
      refreshSkew: const Duration(minutes: 1),
    );

    await service.resolve('1/202607/a.png');
    await service.resolve('1/202607/b.png');
    expect(service.entryCount, 2);

    now = now.add(const Duration(minutes: 9, seconds: 30));

    expect(service.trimExpired(), 2);
    expect(service.entryCount, 0);
  });

  test('removeObjectKeys removes all variants and returns transformed infos',
      () async {
    final now = DateTime.utc(2026, 7, 3, 1);
    final service = DownloadUrlCacheService.withResolver(
      (objectKey, {variant}) async {
        final suffix = variant ?? 'original';
        return _info(
          objectKey:
              suffix == 'playback' ? '1/202607/transcoded/a.mp4' : objectKey,
          url: 'https://cdn.example.com/$suffix',
          expiresAt: now.add(const Duration(minutes: 10)),
          transformed: suffix == 'playback',
        );
      },
      now: () => now,
    );

    await service.resolve('1/202607/a.mov');
    await service.resolve('1/202607/a.mov', variant: 'playback');
    await service.resolve('1/202607/b.png');

    final removed = service.removeObjectKeys(['1/202607/a.mov']);

    expect(removed.map((info) => info.objectKey), [
      '1/202607/a.mov',
      '1/202607/transcoded/a.mp4',
    ]);
    expect(service.entryCount, 1);
  });

  test('rejects empty object key', () async {
    final service = DownloadUrlCacheService.withResolver(
      (objectKey, {variant}) async => _info(
        objectKey: objectKey,
        url: 'https://cdn.example.com/a',
        expiresAt: DateTime.utc(2026, 7, 3, 1),
      ),
    );

    await expectLater(service.resolve(''), throwsArgumentError);
  });
}

FileDownloadInfo _info({
  required String objectKey,
  required String url,
  required DateTime expiresAt,
  bool transformed = false,
}) =>
    FileDownloadInfo(
      objectKey: objectKey,
      url: url,
      expiresAt: expiresAt.millisecondsSinceEpoch,
      transformed: transformed,
    );
