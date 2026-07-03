import 'package:flutter_test/flutter_test.dart';
import 'package:im_app/data/media_mime.dart';

void main() {
  test('detects common mobile and desktop video file names', () {
    expect(mediaMimeFromFileName('clip.MP4'), 'video/mp4');
    expect(mediaMimeFromFileName('camera.MOV'), 'video/quicktime');
    expect(mediaMimeFromFileName('screen-recording.m4v'), 'video/x-m4v');
    expect(mediaMimeFromFileName('archive-preview.mkv'), 'video/x-matroska');
    expect(mediaMimeFromFileName('legacy.avi'), 'video/x-msvideo');
    expect(mediaMimeFromFileName('mobile.3gp'), 'video/3gpp');
    expect(mediaMimeFromFileName('mobile.3g2'), 'video/3gpp2');
    expect(mediaMimeFromFileName('camera.mpeg'), 'video/mpeg');
  });

  test('keeps non-video document and fallback mappings', () {
    expect(mediaMimeFromFileName('photo.heic'), 'image/heic');
    expect(mediaMimeFromFileName('report.docx'),
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
    expect(mediaMimeFromFileName('sheet.xlsx'),
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
    expect(mediaMimeFromFileName('slides.pptx'),
        'application/vnd.openxmlformats-officedocument.presentationml.presentation');
    expect(mediaMimeFromFileName('unknown.bin'), 'application/octet-stream');
  });
}
