import 'package:flutter/material.dart';

import '../../core/theme/lumo_colors.dart';

/// 启动页：恢复登录态期间短暂展示。
class SplashPage extends StatelessWidget {
  const SplashPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _LumoMark(),
            SizedBox(height: 20),
            SizedBox(
              width: 22,
              height: 22,
              child: CircularProgressIndicator(strokeWidth: 2.4),
            ),
          ],
        ),
      ),
    );
  }
}

class _LumoMark extends StatelessWidget {
  const _LumoMark();

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            color: LumoColors.primary,
            borderRadius: BorderRadius.circular(12),
          ),
          child: const Icon(Icons.chat_bubble_rounded,
              color: Colors.white, size: 24),
        ),
        const SizedBox(width: 12),
        const Text(
          '微光 Lumo',
          style: TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
        ),
      ],
    );
  }
}
