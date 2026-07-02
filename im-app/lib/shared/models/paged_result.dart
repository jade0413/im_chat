/// 通用分页结果模型。REST 历史消息、联系人、群成员列表可复用。
class PagedResult<T> {
  const PagedResult({
    required this.items,
    required this.hasMore,
    this.nextCursor,
  });

  final List<T> items;
  final bool hasMore;
  final String? nextCursor;
}
