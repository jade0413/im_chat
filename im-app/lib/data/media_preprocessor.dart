import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:image/image.dart' as img;
import 'package:path/path.dart' as p;
import 'package:video_player/video_player.dart';
import 'package:video_thumbnail/video_thumbnail.dart';

class ImageThumbnail {
  const ImageThumbnail({
    required this.bytes,
    required this.width,
    required this.height,
  });

  final Uint8List bytes;
  final int width;
  final int height;
}

class VideoMetadata {
  const VideoMetadata({
    this.durationMs,
    this.thumbnail,
  });

  final int? durationMs;
  final ImageThumbnail? thumbnail;
}

/// 发送前媒体处理：对齐 OpenIM 的 source/snapshot 思路，但只落当前协议需要的
/// objectKey + thumbKey + durationMs，避免 UI 直接掺上传细节。
class MediaPreprocessor {
  MediaPreprocessor._();

  static ImageThumbnail? imageThumbnail(Uint8List bytes) {
    final decoded = img.decodeImage(bytes);
    if (decoded == null) return null;
    final oriented = img.bakeOrientation(decoded);
    final longSide = math.max(oriented.width, oriented.height);
    final scale = longSide > 520 ? 520 / longSide : 1.0;
    final width = math.max(1, (oriented.width * scale).round());
    final height = math.max(1, (oriented.height * scale).round());
    final thumb = img.copyResize(
      oriented,
      width: width,
      height: height,
      interpolation: img.Interpolation.average,
    );
    return ImageThumbnail(
      bytes: Uint8List.fromList(img.encodeJpg(thumb, quality: 72)),
      width: thumb.width,
      height: thumb.height,
    );
  }

  static Future<VideoMetadata> videoMetadata(String? localPath) async {
    if (localPath == null || localPath.isEmpty) {
      return const VideoMetadata();
    }
    final file = File(localPath);
    if (!await file.exists()) return const VideoMetadata();

    final durationMs = await _videoDurationMs(file);
    final thumb = await _videoThumbnail(localPath);
    return VideoMetadata(durationMs: durationMs, thumbnail: thumb);
  }

  static Future<int?> _videoDurationMs(File file) async {
    VideoPlayerController? controller;
    try {
      controller = VideoPlayerController.file(file);
      await controller.initialize();
      final ms = controller.value.duration.inMilliseconds;
      return ms > 0 ? ms : null;
    } catch (_) {
      return null;
    } finally {
      await controller?.dispose();
    }
  }

  static Future<ImageThumbnail?> _videoThumbnail(String localPath) async {
    try {
      final bytes = await VideoThumbnail.thumbnailData(
        video: localPath,
        imageFormat: ImageFormat.JPEG,
        maxWidth: 640,
        quality: 72,
      );
      if (bytes == null || bytes.isEmpty) return null;
      final decoded = img.decodeImage(bytes);
      return ImageThumbnail(
        bytes: bytes,
        width: decoded?.width ?? 0,
        height: decoded?.height ?? 0,
      );
    } catch (_) {
      return null;
    }
  }

  static String thumbnailFileName(String fileName) {
    final base = p.basenameWithoutExtension(fileName);
    return base.isEmpty ? 'thumb.jpg' : '${base}_thumb.jpg';
  }
}
