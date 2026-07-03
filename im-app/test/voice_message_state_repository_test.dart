import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:im_app/data/local/app_database.dart';
import 'package:im_app/data/repositories/voice_message_state_repository.dart';

void main() {
  late AppDatabase db;
  late VoiceMessageStateRepository repository;

  setUp(() {
    db = AppDatabase.forTesting(NativeDatabase.memory());
    repository = VoiceMessageStateRepository(db.kvDao);
  });

  tearDown(() async {
    await db.close();
  });

  test('voice starts as unplayed and becomes played after marking', () async {
    expect(await repository.isPlayed('m1'), isFalse);

    await repository.markPlayed('m1');

    expect(await repository.isPlayed('m1'), isTrue);
  });

  test('watchPlayed emits local played state changes', () async {
    final events = <bool>[];
    final sub = repository.watchPlayed('m1').listen(events.add);
    addTearDown(sub.cancel);

    await pumpEventQueue();
    await repository.markPlayed('m1');
    await pumpEventQueue();

    expect(events, [false, true]);
  });

  test('blank client message id is ignored', () async {
    await repository.markPlayed('  ');

    expect(await repository.isPlayed('  '), isFalse);
    expect(await db.kvDao.get('voice_played:'), isNull);
  });
}
