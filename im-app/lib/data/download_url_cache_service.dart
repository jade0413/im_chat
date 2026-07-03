import 'remote/rest/file_api.dart';

class DownloadUrlCacheService {
  DownloadUrlCacheService(
    FileApi fileApi, {
    DateTime Function()? now,
    Duration refreshSkew = const Duration(seconds: 60),
  })  : _downloadInfo = fileApi.downloadInfo,
        _now = now ?? DateTime.now,
        _refreshSkew = refreshSkew;

  DownloadUrlCacheService.withResolver(
    this._downloadInfo, {
    DateTime Function()? now,
    Duration refreshSkew = const Duration(seconds: 60),
  })  : _now = now ?? DateTime.now,
        _refreshSkew = refreshSkew;

  final Future<FileDownloadInfo> Function(String objectKey, {String? variant})
      _downloadInfo;
  final DateTime Function() _now;
  final Duration _refreshSkew;
  final Map<String, FileDownloadInfo> _cache = {};

  int get entryCount => _cache.length;

  Future<FileDownloadInfo> resolve(
    String objectKey, {
    String? variant,
    bool forceRefresh = false,
  }) async {
    if (objectKey.isEmpty) {
      throw ArgumentError.value(objectKey, 'objectKey', 'must not be empty');
    }
    final key = _cacheKey(objectKey, variant);
    final cached = _cache[key];
    if (!forceRefresh && cached != null && _isUsable(cached)) {
      return cached;
    }
    final fresh = await _downloadInfo(objectKey, variant: variant);
    if (fresh.url.isEmpty) {
      throw StateError('download url is empty for $objectKey');
    }
    _cache[key] = fresh;
    return fresh;
  }

  Future<String> url(String objectKey, {String? variant}) async {
    final info = await resolve(objectKey, variant: variant);
    return info.url;
  }

  void invalidate(String objectKey, {String? variant}) {
    _cache.remove(_cacheKey(objectKey, variant));
  }

  List<FileDownloadInfo> removeObjectKeys(Iterable<String> objectKeys) {
    final targets = objectKeys
        .map((key) => key.trim())
        .where((key) => key.isNotEmpty)
        .toSet();
    if (targets.isEmpty) return const [];

    final removed = <FileDownloadInfo>[];
    _cache.removeWhere((key, info) {
      final cachedObjectKey = _objectKeyFromCacheKey(key);
      final shouldRemove =
          targets.contains(cachedObjectKey) || targets.contains(info.objectKey);
      if (shouldRemove) removed.add(info);
      return shouldRemove;
    });
    return removed;
  }

  int trimExpired() {
    final before = _cache.length;
    _cache.removeWhere((_, info) => !_isUsable(info));
    return before - _cache.length;
  }

  int clear() {
    final count = _cache.length;
    _cache.clear();
    return count;
  }

  bool _isUsable(FileDownloadInfo info) {
    if (info.url.isEmpty) return false;
    if (info.expiresAt <= 0) return false;
    final refreshAt = DateTime.fromMillisecondsSinceEpoch(
      info.expiresAt,
      isUtc: true,
    ).subtract(_refreshSkew);
    return _now().toUtc().isBefore(refreshAt);
  }

  String _cacheKey(String objectKey, String? variant) =>
      variant == null || variant.isEmpty ? objectKey : '$objectKey#$variant';

  String _objectKeyFromCacheKey(String key) {
    final split = key.indexOf('#');
    return split < 0 ? key : key.substring(0, split);
  }
}
