import 'dart:math';

/// 指数退避重连（移植 im-web socket/reconnect.ts，对齐 protocol.md §13.3 防重连风暴）。
/// 1s → 2s → 4s → … → 60s 封顶，叠加 ±30% 随机抖动避免重连风暴。
class ReconnectBackoff {
  ReconnectBackoff({
    this.baseMs = 1000,
    this.maxMs = 60000,
    this.factor = 2,
    this.jitter = 0.3,
  });

  final int baseMs;
  final int maxMs;
  final double factor;
  final double jitter;

  final Random _rng = Random();
  int _attempt = 0;

  int nextDelay() {
    final exp = baseMs * pow(factor, _attempt);
    final capped = min(exp.toDouble(), maxMs.toDouble());
    _attempt++;
    final delta = capped * jitter;
    final jittered = capped - delta + _rng.nextDouble() * 2 * delta;
    return jittered.round().clamp(0, maxMs);
  }

  void reset() => _attempt = 0;
}
