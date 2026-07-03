import 'package:flutter_test/flutter_test.dart';
import 'package:im_app/data/voice_playback_coordinator.dart';

void main() {
  test('claim broadcasts the active voice token', () async {
    final coordinator = VoicePlaybackCoordinator();
    addTearDown(coordinator.dispose);
    final events = <String?>[];
    final sub = coordinator.activeTokenStream.listen(events.add);
    addTearDown(sub.cancel);

    expect(coordinator.claim('m1'), isTrue);
    expect(coordinator.activeToken, 'm1');
    expect(events, ['m1']);

    expect(coordinator.claim('m1'), isFalse);
    expect(events, ['m1']);
  });

  test('claiming another token replaces the previous owner', () async {
    final coordinator = VoicePlaybackCoordinator();
    addTearDown(coordinator.dispose);
    final events = <String?>[];
    final sub = coordinator.activeTokenStream.listen(events.add);
    addTearDown(sub.cancel);

    coordinator.claim('m1');
    coordinator.claim('m2');

    expect(coordinator.activeToken, 'm2');
    expect(events, ['m1', 'm2']);
  });

  test('release only clears the current owner', () async {
    final coordinator = VoicePlaybackCoordinator();
    addTearDown(coordinator.dispose);
    final events = <String?>[];
    final sub = coordinator.activeTokenStream.listen(events.add);
    addTearDown(sub.cancel);

    coordinator.claim('m1');

    expect(coordinator.release('m2'), isFalse);
    expect(coordinator.activeToken, 'm1');
    expect(coordinator.release('m1'), isTrue);
    expect(coordinator.activeToken, isNull);
    expect(events, ['m1', null]);
  });

  test('rejects empty tokens', () {
    final coordinator = VoicePlaybackCoordinator();
    addTearDown(coordinator.dispose);

    expect(() => coordinator.claim(''), throwsArgumentError);
  });
}
