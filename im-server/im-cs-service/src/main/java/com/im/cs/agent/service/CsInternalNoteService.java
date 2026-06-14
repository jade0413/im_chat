package com.im.cs.agent.service;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.id.SnowflakeIdGenerator;
import com.im.common.tenant.TenantContext;
import com.im.cs.agent.dao.entity.CsInternalNoteEntity;
import com.im.cs.agent.dao.mapper.CsInternalNoteMapper;
import com.im.cs.agent.dto.CsConvItemResponse;
import com.im.cs.agent.dto.CsInternalNoteListResponse;
import com.im.cs.agent.dto.CsInternalNoteResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 坐席内部备注服务。备注不进入消息链路，也不会推给访客。 */
@Service
public class CsInternalNoteService {

  private static final int CS_STATUS_OPEN = 1;
  private static final int NOTE_LIST_LIMIT = 100;

  private final CsInternalNoteMapper noteMapper;
  private final CsAgentService csAgentService;
  private final SnowflakeIdGenerator idGenerator;

  public CsInternalNoteService(CsInternalNoteMapper noteMapper,
      CsAgentService csAgentService,
      SnowflakeIdGenerator idGenerator) {
    this.noteMapper = noteMapper;
    this.csAgentService = csAgentService;
    this.idGenerator = idGenerator;
  }

  public CsInternalNoteListResponse listNotes(long convId, long agentId) {
    long tenantId = TenantContext.requiredTenantId();
    requireOwnedByAgent(convId, agentId);
    List<CsInternalNoteResponse> notes = noteMapper
        .listByConversation(tenantId, convId, NOTE_LIST_LIMIT)
        .stream()
        .map(this::toResponse)
        .toList();
    return new CsInternalNoteListResponse(notes);
  }

  @Transactional
  public CsInternalNoteResponse createNote(long convId, long agentId, String content) {
    long tenantId = TenantContext.requiredTenantId();
    requireOwnedByAgent(convId, agentId);
    String normalized = content == null ? "" : content.trim();
    if (normalized.isBlank()) {
      throw new ImException(ErrorCode.VALIDATION_FAILED, "备注内容不能为空");
    }
    CsInternalNoteEntity note = new CsInternalNoteEntity();
    note.setId(idGenerator.nextId());
    note.setTenantId(tenantId);
    note.setConvId(convId);
    note.setAgentId(agentId);
    note.setContent(normalized);
    note.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
    noteMapper.insert(note);
    return toResponse(note);
  }

  // 备注权限：会话必须已认领（非 open），且 agent_id 为当前坐席本人。
  // 结单后 agent_id 仍保留为处理坐席（见 ConversationMapper.resolveConversation），
  // 因此本人可在结单后继续查看/补充备注（质检/交接）。
  private void requireOwnedByAgent(long convId, long agentId) {
    CsConvItemResponse conv = csAgentService.getConv(convId, agentId);
    if (conv.csStatus() == CS_STATUS_OPEN || !Objects.equals(conv.agentId(), agentId)) {
      throw new ImException(ErrorCode.NO_PERMISSION, "仅处理该会话的坐席可以查看或添加内部备注");
    }
  }

  private CsInternalNoteResponse toResponse(CsInternalNoteEntity note) {
    long createdAtMs = note.getCreatedAt() == null
        ? 0L
        : note.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli();
    return new CsInternalNoteResponse(
        note.getId(),
        note.getConvId(),
        note.getAgentId(),
        note.getContent(),
        createdAtMs);
  }
}
