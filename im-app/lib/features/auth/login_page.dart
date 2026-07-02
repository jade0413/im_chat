import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';
import '../../core/theme/lumo_colors.dart';
import '../../core/theme/lumo_theme.dart';

/// 登录页（账号密码登录，对齐设计稿：Logo + 欢迎语 + 账号/密码 + 登录按钮）。
class LoginPage extends ConsumerStatefulWidget {
  const LoginPage({super.key});

  @override
  ConsumerState<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends ConsumerState<LoginPage> {
  final _account = TextEditingController();
  final _password = TextEditingController();
  final _nickname = TextEditingController();
  _AccountMode _accountMode = _AccountMode.account;
  bool _obscure = true;
  bool _submitting = false;
  bool _registerMode = false;

  @override
  void dispose() {
    _account.dispose();
    _password.dispose();
    _nickname.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final account = _account.text.trim();
    final pwd = _password.text;
    if (account.isEmpty) {
      _toast('请输入账号');
      return;
    }
    if (pwd.length < 8) {
      _toast('密码至少 8 位');
      return;
    }
    setState(() => _submitting = true);
    final notifier = ref.read(authControllerProvider.notifier);
    final ok = _registerMode
        ? await notifier.register(account, pwd,
            nickname:
                _nickname.text.trim().isEmpty ? null : _nickname.text.trim())
        : await notifier.login(account, pwd);
    if (mounted) setState(() => _submitting = false);
    if (!ok && mounted) {
      _toast(ref.read(authControllerProvider).loginError ?? '操作失败');
    }
  }

  void _toast(String msg) =>
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));

  @override
  Widget build(BuildContext context) {
    final kick = ref.watch(authControllerProvider).kickMessage;
    return Theme(
      data: LumoTheme.light(),
      child: Scaffold(
        backgroundColor: LumoColors.surface,
        body: SafeArea(
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 430),
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(32, 86, 32, 28),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Align(
                      alignment: Alignment.centerLeft,
                      child: Container(
                        width: 72,
                        height: 72,
                        decoration: BoxDecoration(
                          color: LumoColors.primary,
                          borderRadius: BorderRadius.circular(20),
                          boxShadow: const [
                            BoxShadow(
                              color: Color(0x335A54F0),
                              blurRadius: 34,
                              offset: Offset(0, 16),
                            ),
                          ],
                        ),
                        child: const Icon(Icons.chat_bubble_rounded,
                            color: Colors.white, size: 36),
                      ),
                    ),
                    const SizedBox(height: 34),
                    Text(
                      _registerMode ? '创建账号' : '欢迎回来',
                      style: const TextStyle(
                        color: LumoColors.ink,
                        fontSize: 34,
                        height: 1.18,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      _registerMode ? '注册微光 Lumo 账号' : '登录 微光 Lumo，继续你的对话',
                      style: const TextStyle(
                        color: LumoColors.textSecondary,
                        fontSize: 16,
                        height: 1.4,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    if (kick != null) ...[
                      const SizedBox(height: 14),
                      Text(kick,
                          style: const TextStyle(
                              color: LumoColors.danger, fontSize: 13)),
                    ],
                    const SizedBox(height: 34),
                    _AccountModeSwitch(
                      value: _accountMode,
                      onChanged: (mode) => setState(() => _accountMode = mode),
                    ),
                    const SizedBox(height: 20),
                    TextField(
                      controller: _account,
                      keyboardType: _keyboardTypeFor(_accountMode),
                      autofillHints: _autofillHintsFor(_accountMode),
                      textInputAction: TextInputAction.next,
                      decoration: _inputDecoration(
                        icon: _iconFor(_accountMode),
                        hintText: _hintFor(_accountMode),
                      ),
                    ),
                    const SizedBox(height: 14),
                    TextField(
                      controller: _password,
                      obscureText: _obscure,
                      onSubmitted: (_) => _submit(),
                      decoration: _inputDecoration(
                        icon: Icons.lock_outline,
                        hintText: '密码（至少 8 位）',
                        suffixIcon: IconButton(
                          icon: Icon(_obscure
                              ? Icons.visibility_off_outlined
                              : Icons.visibility_outlined),
                          onPressed: () => setState(() => _obscure = !_obscure),
                        ),
                      ),
                    ),
                    if (_registerMode) ...[
                      const SizedBox(height: 14),
                      TextField(
                        controller: _nickname,
                        textInputAction: TextInputAction.done,
                        decoration: _inputDecoration(
                          icon: Icons.badge_outlined,
                          hintText: '昵称（可选）',
                        ),
                      ),
                    ],
                    Align(
                      alignment: Alignment.centerRight,
                      child: TextButton(
                        onPressed: _submitting
                            ? null
                            : () => _toast('密码找回暂未开放，请联系管理员重置'),
                        child: const Text('忘记密码？'),
                      ),
                    ),
                    const SizedBox(height: 12),
                    SizedBox(
                      height: 58,
                      child: FilledButton(
                        onPressed: _submitting ? null : _submit,
                        child: _submitting
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(
                                    strokeWidth: 2, color: Colors.white))
                            : Text(_registerMode ? '注册并登录' : '登录'),
                      ),
                    ),
                    const SizedBox(height: 30),
                    const _OtherLoginDivider(),
                    const SizedBox(height: 18),
                    _SocialLoginRow(
                      onTap: () => _toast('该登录方式暂未开放，请使用账号密码登录'),
                    ),
                    const SizedBox(height: 48),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          _registerMode ? '已有账号？' : '还没有账号？',
                          style: const TextStyle(
                            color: LumoColors.textSecondary,
                            fontSize: 15,
                          ),
                        ),
                        TextButton(
                          onPressed: _submitting
                              ? null
                              : () => setState(
                                  () => _registerMode = !_registerMode),
                          child: Text(_registerMode ? '登录' : '注册'),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  TextInputType _keyboardTypeFor(_AccountMode mode) {
    switch (mode) {
      case _AccountMode.phone:
        return TextInputType.phone;
      case _AccountMode.email:
        return TextInputType.emailAddress;
      case _AccountMode.account:
        return TextInputType.text;
    }
  }

  Iterable<String> _autofillHintsFor(_AccountMode mode) {
    switch (mode) {
      case _AccountMode.phone:
        return const [AutofillHints.telephoneNumber];
      case _AccountMode.email:
        return const [AutofillHints.email];
      case _AccountMode.account:
        return const [AutofillHints.username];
    }
  }

  IconData _iconFor(_AccountMode mode) {
    switch (mode) {
      case _AccountMode.phone:
        return Icons.phone_iphone_rounded;
      case _AccountMode.email:
        return Icons.mail_outline;
      case _AccountMode.account:
        return Icons.person_outline;
    }
  }

  String _hintFor(_AccountMode mode) {
    switch (mode) {
      case _AccountMode.phone:
        return '请输入手机号';
      case _AccountMode.email:
        return 'chen@lumo.im';
      case _AccountMode.account:
        return '请输入账号';
    }
  }

  InputDecoration _inputDecoration({
    required IconData icon,
    required String hintText,
    Widget? suffixIcon,
  }) {
    return InputDecoration(
      prefixIcon: Icon(icon, color: LumoColors.textSecondary),
      hintText: hintText,
      suffixIcon: suffixIcon,
      filled: true,
      fillColor: LumoColors.surfaceAlt,
      contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 20),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(16),
        borderSide: const BorderSide(color: Color(0xFFE2E4EC), width: 1.2),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(16),
        borderSide: const BorderSide(color: Color(0xFFE2E4EC), width: 1.2),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(16),
        borderSide: const BorderSide(color: LumoColors.primary, width: 1.8),
      ),
    );
  }
}

enum _AccountMode { account, phone, email }

class _AccountModeSwitch extends StatelessWidget {
  const _AccountModeSwitch({
    required this.value,
    required this.onChanged,
  });

  final _AccountMode value;
  final ValueChanged<_AccountMode> onChanged;

  @override
  Widget build(BuildContext context) {
    final selectedIndex = _AccountMode.values.indexOf(value);
    return Container(
      height: 52,
      decoration: BoxDecoration(
        color: LumoColors.surfaceAlt,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Stack(
        children: [
          AnimatedAlign(
            duration: const Duration(milliseconds: 180),
            curve: Curves.easeOutCubic,
            alignment: Alignment(selectedIndex - 1.0, 0),
            child: FractionallySizedBox(
              widthFactor: 1 / 3,
              heightFactor: 1,
              child: Padding(
                padding: const EdgeInsets.all(4),
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: LumoColors.surface,
                    borderRadius: BorderRadius.circular(14),
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x1416171D),
                        blurRadius: 16,
                        offset: Offset(0, 6),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          Row(
            children: [
              _ModeButton(
                label: '账号',
                selected: value == _AccountMode.account,
                onTap: () => onChanged(_AccountMode.account),
              ),
              _ModeButton(
                label: '手机号',
                selected: value == _AccountMode.phone,
                onTap: () => onChanged(_AccountMode.phone),
              ),
              _ModeButton(
                label: '邮箱',
                selected: value == _AccountMode.email,
                onTap: () => onChanged(_AccountMode.email),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _ModeButton extends StatelessWidget {
  const _ModeButton({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: onTap,
        child: Center(
          child: Text(
            label,
            style: TextStyle(
              color: selected ? LumoColors.ink : LumoColors.textSecondary,
              fontSize: 16,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
      ),
    );
  }
}

class _OtherLoginDivider extends StatelessWidget {
  const _OtherLoginDivider();

  @override
  Widget build(BuildContext context) {
    return const Row(
      children: [
        Expanded(child: Divider(color: LumoColors.divider)),
        Padding(
          padding: EdgeInsets.symmetric(horizontal: 14),
          child: Text(
            '其他方式',
            style: TextStyle(color: LumoColors.textSecondary, fontSize: 13),
          ),
        ),
        Expanded(child: Divider(color: LumoColors.divider)),
      ],
    );
  }
}

class _SocialLoginRow extends StatelessWidget {
  const _SocialLoginRow({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        _SocialButton(
          icon: Icons.wechat,
          color: LumoColors.success,
          onTap: onTap,
        ),
        const SizedBox(width: 18),
        _SocialButton(
          icon: Icons.phone_iphone_rounded,
          color: const Color(0xFF3494FF),
          onTap: onTap,
        ),
        const SizedBox(width: 18),
        _SocialButton(
          icon: Icons.apple_rounded,
          color: LumoColors.ink,
          onTap: onTap,
        ),
      ],
    );
  }
}

class _SocialButton extends StatelessWidget {
  const _SocialButton({
    required this.icon,
    required this.color,
    required this.onTap,
  });

  final IconData icon;
  final Color color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(27),
      onTap: onTap,
      child: Container(
        width: 54,
        height: 54,
        decoration: const BoxDecoration(
          color: LumoColors.surfaceAlt,
          shape: BoxShape.circle,
        ),
        child: Icon(icon, color: color, size: 26),
      ),
    );
  }
}
