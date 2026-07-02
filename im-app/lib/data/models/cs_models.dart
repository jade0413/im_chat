class CsConversation {
  const CsConversation({
    required this.convId,
    required this.csStatus,
    required this.agentId,
    required this.visitorUserId,
    required this.visitorName,
    required this.lastMsgTimeMs,
    required this.lastMsgAbstract,
    required this.maxSeq,
    this.visitorOnline = false,
    this.visitorReadSeq = '0',
  });

  final String convId;
  final int csStatus; // 1=open 2=assigned 3=resolved
  final String agentId;
  final String visitorUserId;
  final String visitorName;
  final bool visitorOnline;
  final String visitorReadSeq;
  final String lastMsgTimeMs;
  final String lastMsgAbstract;
  final String maxSeq;

  bool get isOpen => csStatus == 1;
  bool get isAssigned => csStatus == 2;
  bool get isResolved => csStatus == 3;

  factory CsConversation.fromJson(Map<String, dynamic> j) => CsConversation(
        convId: (j['convId'] ?? '0').toString(),
        csStatus: (j['csStatus'] as num?)?.toInt() ?? 0,
        agentId: (j['agentId'] ?? '0').toString(),
        visitorUserId: (j['visitorUserId'] ?? '0').toString(),
        visitorName: (j['visitorName'] ?? '访客').toString(),
        visitorOnline: j['visitorOnline'] == true,
        visitorReadSeq: (j['visitorReadSeq'] ?? '0').toString(),
        lastMsgTimeMs: (j['lastMsgTimeMs'] ?? '0').toString(),
        lastMsgAbstract: (j['lastMsgAbstract'] ?? '').toString(),
        maxSeq: (j['maxSeq'] ?? '0').toString(),
      );
}

class CsConversationList {
  const CsConversationList({required this.convs, required this.hasMore});

  final List<CsConversation> convs;
  final bool hasMore;

  factory CsConversationList.fromJson(Map<String, dynamic> j) {
    final list = (j['convs'] as List?) ?? const [];
    return CsConversationList(
      convs: list
          .map((e) => CsConversation.fromJson(e as Map<String, dynamic>))
          .toList(),
      hasMore: j['hasMore'] == true,
    );
  }
}

class CsInternalNote {
  const CsInternalNote({
    required this.id,
    required this.convId,
    required this.agentId,
    required this.content,
    required this.createdAtMs,
  });

  final String id;
  final String convId;
  final String agentId;
  final String content;
  final String createdAtMs;

  factory CsInternalNote.fromJson(Map<String, dynamic> j) => CsInternalNote(
        id: (j['id'] ?? '0').toString(),
        convId: (j['convId'] ?? '0').toString(),
        agentId: (j['agentId'] ?? '0').toString(),
        content: (j['content'] ?? '').toString(),
        createdAtMs: (j['createdAtMs'] ?? '0').toString(),
      );
}
