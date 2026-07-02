import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/theme/lumo_theme.dart';
import 'providers.dart';
import 'router.dart';

/// 微光 Lumo 应用根。MaterialApp.router + Lumo 主题 + 中文本地化。
/// 监听应用前台/后台：回前台时让连接层立即探活/重连（自有重连设计的一环）。
class LumoApp extends ConsumerStatefulWidget {
  const LumoApp({super.key});

  @override
  ConsumerState<LumoApp> createState() => _LumoAppState();
}

class _LumoAppState extends ConsumerState<LumoApp> with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      ref.read(imEngineProvider).onAppResumed();
    }
  }

  @override
  Widget build(BuildContext context) {
    final router = ref.watch(routerProvider);
    return MaterialApp.router(
      title: '微光 Lumo',
      debugShowCheckedModeBanner: false,
      theme: LumoTheme.light(),
      darkTheme: LumoTheme.dark(),
      themeMode: ThemeMode.system,
      routerConfig: router,
      locale: const Locale('zh'),
      supportedLocales: const [Locale('zh'), Locale('en')],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
    );
  }
}
