package com.im.cs.agent.rest;

import com.im.common.auth.UserContext;
import com.im.common.web.ApiResponse;
import com.im.cs.agent.dto.AgentConvListResponse;
import com.im.cs.agent.dto.CreateCsInternalNoteRequest;
import com.im.cs.agent.dto.CsConvItemResponse;
import com.im.cs.agent.dto.CsInternalNoteListResponse;
import com.im.cs.agent.dto.CsInternalNoteResponse;
import com.im.cs.agent.service.CsAgentService;
import com.im.cs.agent.service.CsInternalNoteService;
import com.im.cs.agent.service.CsAgentValidationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CS 坐席操作 API（T35）。
 *
 * <p>全部接口需要有效 JWT（由 JwtAuthInterceptor 统一鉴权），
 * 并且 is_agent=1（由 CsAgentValidationService 校验）。
 *
 * <pre>
 * GET  /api/v1/cs/conversations          坐席工作台：open 全部 + 本人 assigned
 * GET  /api/v1/cs/conversations/{convId} 查看单个 CS 会话详情
 * POST /api/v1/cs/conversations/{convId}/claim   认领 open 会话
 * POST /api/v1/cs/conversations/{convId}/resolve 结单 assigned 会话
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/cs/conversations")
public class CsAgentController {

  private final CsAgentService csAgentService;
  private final CsInternalNoteService noteService;
  private final CsAgentValidationService validationService;

  public CsAgentController(CsAgentService csAgentService,
      CsInternalNoteService noteService,
      CsAgentValidationService validationService) {
    this.csAgentService = csAgentService;
    this.noteService = noteService;
    this.validationService = validationService;
  }

  /**
   * 坐席工作台：open 的所有会话 + 分配给我的 assigned 会话，按最新消息时间倒序。
   *
   * <pre>
   * GET /api/v1/cs/conversations?limit=20&offset=0
   * </pre>
   */
  @GetMapping
  public ApiResponse<AgentConvListResponse> listConvs(
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    long agentId = currentAgentId();
    return ApiResponse.ok(csAgentService.listConvs(agentId, limit, offset));
  }

  /**
   * 查看单个 CS 会话元数据（坐席确认访客信息）。
   *
   * <pre>
   * GET /api/v1/cs/conversations/{convId}
   * </pre>
   */
  @GetMapping("/{convId}")
  public ApiResponse<CsConvItemResponse> getConv(@PathVariable long convId) {
    long agentId = currentAgentId();
    return ApiResponse.ok(csAgentService.getConv(convId, agentId));
  }

  /**
   * 认领 open 会话（D31: open → assigned）。
   *
   * <pre>
   * POST /api/v1/cs/conversations/{convId}/claim
   * </pre>
   */
  @PostMapping("/{convId}/claim")
  public ApiResponse<Void> claim(@PathVariable long convId) {
    long agentId = currentActiveAgentId();
    csAgentService.claimConv(convId, agentId);
    return ApiResponse.ok(null);
  }

  /**
   * 结单（D31: assigned → resolved）。只有当前绑定坐席可操作。
   *
   * <pre>
   * POST /api/v1/cs/conversations/{convId}/resolve
   * </pre>
   */
  @PostMapping("/{convId}/resolve")
  public ApiResponse<Void> resolve(@PathVariable long convId) {
    long agentId = currentAgentId();
    csAgentService.resolveConv(convId, agentId);
    return ApiResponse.ok(null);
  }

  /** 查看当前已认领会话的内部备注。未认领会话不允许查看备注。 */
  @GetMapping("/{convId}/notes")
  public ApiResponse<CsInternalNoteListResponse> listNotes(@PathVariable long convId) {
    long agentId = currentAgentId();
    return ApiResponse.ok(noteService.listNotes(convId, agentId));
  }

  /** 添加内部备注。备注仅坐席侧可见，不推送给访客。 */
  @PostMapping("/{convId}/notes")
  public ApiResponse<CsInternalNoteResponse> createNote(@PathVariable long convId,
      @Valid @RequestBody CreateCsInternalNoteRequest request) {
    long agentId = currentAgentId();
    return ApiResponse.ok(noteService.createNote(convId, agentId, request.content()));
  }

  /** 获取当前请求的坐席 userId，并校验其 is_agent=1 权限。 */
  private long currentAgentId() {
    long userId = UserContext.requiredUserId();
    validationService.requireAgent(userId);
    return userId;
  }

  private long currentActiveAgentId() {
    long userId = UserContext.requiredUserId();
    validationService.requireActiveAgent(userId);
    return userId;
  }
}
