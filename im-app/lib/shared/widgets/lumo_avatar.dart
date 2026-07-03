import 'dart:io';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';

/// Lumo 头像：有图显图，无图显首字母 + 按名稳定取色（对齐设计稿的彩色字母头像）。
class LumoAvatar extends ConsumerWidget {
  const LumoAvatar({
    super.key,
    required this.name,
    this.url,
    this.size = 44,
    this.radius = 12,
  });

  final String name;
  final String? url;
  final double size;
  final double radius;

  static const _palette = [
    Color(0xFF5A54F0), // 紫
    Color(0xFF21C16B), // 绿
    Color(0xFFF59E0B), // 橙
    Color(0xFF3B82F6), // 蓝
    Color(0xFFEF4444), // 红
    Color(0xFF14B8A6), // 青
    Color(0xFFEC4899), // 粉
  ];

  Color get _color {
    if (name.isEmpty) return _palette.first;
    final h = name.codeUnits.fold<int>(0, (a, c) => (a + c) % _palette.length);
    return _palette[h];
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final borderRadius = BorderRadius.circular(radius);
    if (url != null && url!.isNotEmpty) {
      if (!_isHttpUrl(url!)) {
        final cached = ref.watch(fileCacheProvider(url!));
        return ClipRRect(
          borderRadius: borderRadius,
          child: cached.when(
            data: (file) => Image.file(
              File(file.path),
              width: size,
              height: size,
              fit: BoxFit.cover,
              errorBuilder: (_, __, ___) => _initials(),
            ),
            loading: _initials,
            error: (_, __) => _initials(),
          ),
        );
      }
      return ClipRRect(
        borderRadius: borderRadius,
        child: CachedNetworkImage(
          imageUrl: url!,
          width: size,
          height: size,
          fit: BoxFit.cover,
          placeholder: (_, __) => _initials(),
          errorWidget: (_, __, ___) => _initials(),
        ),
      );
    }
    return _initials();
  }

  bool _isHttpUrl(String value) {
    final uri = Uri.tryParse(value);
    return uri != null && (uri.scheme == 'http' || uri.scheme == 'https');
  }

  Widget _initials() {
    final ch = name.isEmpty ? '?' : name.characters.first;
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: _color,
        borderRadius: BorderRadius.circular(radius),
      ),
      alignment: Alignment.center,
      child: Text(
        ch,
        style: TextStyle(
          color: Colors.white,
          fontSize: size * 0.42,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}
