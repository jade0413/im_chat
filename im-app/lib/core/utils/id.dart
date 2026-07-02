import 'package:fixnum/fixnum.dart';

/// 大整数 id / seq 处理（移植 im-web utils/id.ts）。
///
/// 约定：**模型层统一用十进制 String 承载 id/seq**，避免跨 SQLite 存储与
/// Dart int(64位) 边界的精度风险；只有在 proto 编解码边界才转 Int64。
/// 这与 im-web「store 里 id 全是 string」的策略一致。
class Ids {
  Ids._();

  /// 任意来源 → 十进制字符串（null/空 → "0"）。
  static String toStr(Object? value) {
    if (value == null) return '0';
    if (value is Int64) return value.toString();
    if (value is BigInt) return value.toString();
    if (value is int) return value.toString();
    final s = value.toString();
    return s.isEmpty ? '0' : s;
  }

  /// 字符串 → Int64（写入 proto 字段）。无符号语义由各字段决定，这里按有符号解析，
  /// 足够覆盖 snowflake / seq 的正数范围。
  static Int64 toInt64(Object? value) {
    if (value is Int64) return value;
    final s = toStr(value);
    return Int64.parseInt(s);
  }

  /// 比较两个 id-like（十进制串），返回 -1 / 0 / 1。用 BigInt 避免溢出。
  static int compare(Object? a, Object? b) {
    final left = BigInt.parse(toStr(a));
    final right = BigInt.parse(toStr(b));
    return left.compareTo(right);
  }

  static bool isZeroOrEmpty(Object? value) {
    final s = toStr(value);
    return s == '0';
  }
}
