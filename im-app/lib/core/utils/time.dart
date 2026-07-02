import 'package:intl/intl.dart';

/// 时间格式化（移植 im-web utils/time.ts）。
/// 兼容秒/毫秒时间戳：> 1e10 视为毫秒，否则秒。
class TimeFmt {
  TimeFmt._();

  static DateTime _toDate(Object? value) {
    final ts = int.tryParse(value?.toString() ?? '') ?? 0;
    return DateTime.fromMillisecondsSinceEpoch(
        ts > 10000000000 ? ts : ts * 1000);
  }

  /// 会话列表时间：今天显示 HH:mm，昨天显示「昨天」，今年 M月D日，否则 yyyy/M/d。
  static String chatTime(Object? value) {
    if (value == null || value.toString().isEmpty || value.toString() == '0') {
      return '';
    }
    final date = _toDate(value);
    final now = DateTime.now();
    final d0 = DateTime(date.year, date.month, date.day);
    final today = DateTime(now.year, now.month, now.day);
    final diffDays = today.difference(d0).inDays;
    if (diffDays == 0) return DateFormat('HH:mm').format(date);
    if (diffDays == 1) return '昨天';
    if (date.year == now.year) return DateFormat('M月d日').format(date);
    return DateFormat('yyyy/M/d').format(date);
  }

  /// 气泡内时钟：固定 HH:mm。
  static String messageClock(Object? value) {
    if (value == null || value.toString().isEmpty || value.toString() == '0') {
      return '';
    }
    return DateFormat('HH:mm').format(_toDate(value));
  }
}
