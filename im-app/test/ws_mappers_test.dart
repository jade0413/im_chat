import 'package:fixnum/fixnum.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:im_app/core/proto/codec.dart' as pb;
import 'package:im_app/data/models/message_content.dart';
import 'package:im_app/data/remote/ws/ws_mappers.dart';

void main() {
  test('encodes video body as file content with playback metadata', () {
    final proto = WsMappers.contentToProto(
      const VideoBody(
        objectKey: '1/202607/a.m4v',
        fileName: 'clip.m4v',
        size: 4096,
        mime: 'video/x-m4v',
        thumbKey: '1/202607/a-thumb.jpg',
        durationMs: 12000,
      ),
    );

    expect(proto.hasFile(), isTrue);
    expect(proto.file.objectKey, '1/202607/a.m4v');
    expect(proto.file.fileName, 'clip.m4v');
    expect(proto.file.size, Int64(4096));
    expect(proto.file.mime, 'video/x-m4v');
    expect(proto.file.thumbKey, '1/202607/a-thumb.jpg');
    expect(proto.file.durationMs, 12000);
  });

  test('decodes video file content back to video body', () {
    final content = pb.MsgContent()
      ..file = (pb.FileContent()
        ..objectKey = '1/202607/a.mp4'
        ..fileName = 'clip.mp4'
        ..size = Int64(8192)
        ..mime = 'video/mp4'
        ..thumbKey = '1/202607/a-thumb.jpg'
        ..durationMs = 34000);

    final mapped = WsMappers.protoContentToContent(content);

    expect(mapped, isA<VideoBody>());
    final video = mapped as VideoBody;
    expect(video.objectKey, '1/202607/a.mp4');
    expect(video.fileName, 'clip.mp4');
    expect(video.size, 8192);
    expect(video.mime, 'video/mp4');
    expect(video.thumbKey, '1/202607/a-thumb.jpg');
    expect(video.durationMs, 34000);
  });

  test('keeps non-video file content as generic file body', () {
    final content = pb.MsgContent()
      ..file = (pb.FileContent()
        ..objectKey = '1/202607/a.pdf'
        ..fileName = 'a.pdf'
        ..size = Int64(2048)
        ..mime = 'application/pdf');

    final mapped = WsMappers.protoContentToContent(content);

    expect(mapped, isA<FileBody>());
    final file = mapped as FileBody;
    expect(file.objectKey, '1/202607/a.pdf');
    expect(file.fileName, 'a.pdf');
    expect(file.size, 2048);
    expect(file.mime, 'application/pdf');
  });
}
