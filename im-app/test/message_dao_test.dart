import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:im_app/data/local/app_database.dart';
import 'package:im_app/data/models/chat_message.dart';
import 'package:im_app/data/models/enums.dart';
import 'package:im_app/data/models/message_content.dart';
import 'package:im_app/data/models/sender_info.dart';

void main() {
  late AppDatabase db;

  setUp(() {
    db = AppDatabase.forTesting(NativeDatabase.memory());
  });

  tearDown(() async {
    await db.close();
  });

  test('getBySeq finds message inside the requested conversation only',
      () async {
    await db.messageDao.upsert(_message('conv-a', '42', 'a-42'));
    await db.messageDao.upsert(_message('conv-b', '42', 'b-42'));

    final found = await db.messageDao.getBySeq('conv-a', '42');
    final missing = await db.messageDao.getBySeq('conv-a', '43');

    expect(found?.clientMsgId, 'a-42');
    expect(found?.content, isA<TextBody>());
    expect(missing, isNull);
  });

  test('mediaObjectKeysForConv collects media keys in conversation only',
      () async {
    await db.messageDao.upsert(_message(
      'conv-a',
      '1',
      'image-1',
      content: const ImageBody(
        objectKey: '1/202607/image.jpg',
        thumbKey: '1/202607/image_thumb.jpg',
      ),
    ));
    await db.messageDao.upsert(_message(
      'conv-a',
      '2',
      'voice-1',
      content: const VoiceBody(
        objectKey: '1/202607/voice.m4a',
        durationMs: 1200,
      ),
    ));
    await db.messageDao.upsert(_message(
      'conv-a',
      '3',
      'video-1',
      content: const VideoBody(
        objectKey: '1/202607/video.mov',
        fileName: 'video.mov',
        thumbKey: '1/202607/video_thumb.jpg',
      ),
    ));
    await db.messageDao.upsert(_message(
      'conv-b',
      '1',
      'file-1',
      content: const FileBody(
        objectKey: '1/202607/file.pdf',
        fileName: 'file.pdf',
      ),
    ));

    final keys = await db.messageDao.mediaObjectKeysForConv('conv-a');

    expect(keys, [
      '1/202607/image.jpg',
      '1/202607/image_thumb.jpg',
      '1/202607/voice.m4a',
      '1/202607/video.mov',
      '1/202607/video_thumb.jpg',
    ]);
  });
}

ChatMessage _message(
  String convId,
  String seq,
  String clientMsgId, {
  MessageContent content = const TextBody('hello'),
}) {
  return ChatMessage(
    clientMsgId: clientMsgId,
    convId: convId,
    serverMsgId: 'server-$clientMsgId',
    seq: seq,
    sender: const SenderInfo(userId: '1001', nickname: '用户1001'),
    content: content,
    sendTime: '1000',
    status: MessageStatus.sent,
  );
}
