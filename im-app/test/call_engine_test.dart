import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:im_app/core/proto/codec.dart' as pb;
import 'package:im_app/core/utils/id.dart';
import 'package:im_app/data/call/call_engine.dart';

void main() {
  test('startGroupCall sends group invite target', () async {
    pb.Cmd? sentCmd;
    Uint8List? sentBody;
    final engine = CallEngine(sendFrame: (cmd, body) {
      sentCmd = cmd;
      sentBody = body;
      return true;
    });

    final ok = await engine.startGroupCall(
      '9001',
      groupName: '产品群',
      media: CallMedia.video,
    );

    expect(ok, isTrue);
    expect(sentCmd, pb.Cmd.CALL_INVITE);
    final invite = pb.CallInvite.fromBuffer(sentBody!);
    expect(invite.groupId.toString(), '9001');
    expect(invite.calleeUserId.toString(), '0');
    expect(invite.media, pb.CallMediaType.CALL_MEDIA_VIDEO);
    expect(engine.state.isGroupCall, isTrue);
    expect(engine.state.groupId, '9001');
    expect(engine.state.peerName, '产品群');

    await engine.dispose();
  });

  test('group member hangup removes participant without ending call', () async {
    final engine = CallEngine(sendFrame: (_, __) => true);
    engine.onCallNotify(_groupNotify(
      event: pb.CallEvent.CALL_EVENT_INVITE,
      peerUserId: '10',
      participants: const ['10', '20', '30'],
    ));
    await engine.accept();

    engine.onCallNotify(_groupNotify(
      event: pb.CallEvent.CALL_EVENT_HANGUP,
      peerUserId: '30',
      participants: const ['10', '20'],
    ));
    await Future<void>.delayed(Duration.zero);

    expect(engine.state.phase, CallPhase.connecting);
    expect(engine.state.participantUserIds, ['10', '20']);

    await engine.dispose();
  });

  test('group caller hangup ends call for participants', () async {
    final engine = CallEngine(sendFrame: (_, __) => true);
    engine.onCallNotify(_groupNotify(
      event: pb.CallEvent.CALL_EVENT_INVITE,
      peerUserId: '10',
      participants: const ['10', '20', '30'],
    ));
    await engine.accept();

    engine.onCallNotify(_groupNotify(
      event: pb.CallEvent.CALL_EVENT_HANGUP,
      peerUserId: '10',
      participants: const ['10', '20'],
    ));
    await Future<void>.delayed(Duration.zero);

    expect(engine.state.phase, CallPhase.ended);
    expect(engine.state.endReason, CallEndReason.hangup);

    await engine.dispose();
  });

  test('incoming call times out locally', () async {
    final engine = CallEngine(
      sendFrame: (_, __) => true,
      incomingTimeout: const Duration(milliseconds: 1),
    );

    engine.onCallNotify(pb.CallNotify()
      ..callId = 'call-1'
      ..event = pb.CallEvent.CALL_EVENT_INVITE
      ..peerUserId = Ids.toInt64('10')
      ..media = pb.CallMediaType.CALL_MEDIA_VOICE);

    expect(engine.state.phase, CallPhase.incoming);

    await Future<void>.delayed(const Duration(milliseconds: 5));

    expect(engine.state.phase, CallPhase.ended);
    expect(engine.state.endReason, CallEndReason.timeout);
    await engine.dispose();
  });
}

pb.CallNotify _groupNotify({
  required pb.CallEvent event,
  required String peerUserId,
  required List<String> participants,
}) {
  return pb.CallNotify()
    ..callId = 'call-1'
    ..event = event
    ..peerUserId = Ids.toInt64(peerUserId)
    ..groupId = Ids.toInt64('9001')
    ..media = pb.CallMediaType.CALL_MEDIA_VIDEO
    ..participantUserIds.addAll(participants.map(Ids.toInt64));
}
