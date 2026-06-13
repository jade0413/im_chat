package com.im.cs.visitor.service;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.id.SnowflakeIdGenerator;
import com.im.common.tenant.TenantContext;
import com.im.cs.visitor.dao.entity.VisitorProfileEntity;
import com.im.cs.visitor.dao.mapper.VisitorProfileMapper;
import com.im.cs.visitor.dto.WidgetSessionRequest;
import com.im.cs.visitor.dto.WidgetSessionResponse;
import com.im.proto.rpc.ConversationRpcGrpc;
import com.im.proto.rpc.FindOrCreateCsConvReq;
import com.im.proto.rpc.FindOrCreateCsConvResp;
import com.im.proto.rpc.IssueVisitorTokenReq;
import com.im.proto.rpc.IssueVisitorTokenResp;
import com.im.proto.rpc.ProvisionVisitorUserReq;
import com.im.proto.rpc.ProvisionVisitorUserResp;
import com.im.proto.rpc.UserRpcGrpc;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 访客接入 Widget 的核心逻辑（T31）。
 *
 * <p>流程：
 * <ol>
 *   <li>查 visitor_profile —— 已有则取 userId，否则：</li>
 *   <li>生成 displayName = "访客" + 4 位随机大写字母数字</li>
 *   <li>gRPC ProvisionVisitorUser → 在 user 表创建访客用户</li>
 *   <li>本地写 visitor_profile（同事务）</li>
 *   <li>gRPC IssueVisitorToken → 签发 JWT</li>
 *   <li>gRPC FindOrCreateCsConv → 取或建 CS 会话</li>
 * </ol>
 */
@Service
public class VisitorSessionService {

  private static final String DISPLAY_NAME_PREFIX = "访客";
  private static final String SUFFIX_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 去除易混淆字符
  private static final int SUFFIX_LENGTH = 4;

  private final VisitorProfileMapper visitorProfileMapper;
  private final UserRpcGrpc.UserRpcBlockingStub userRpcStub;
  private final ConversationRpcGrpc.ConversationRpcBlockingStub conversationRpcStub;
  private final SnowflakeIdGenerator idGenerator;
  private final SecureRandom random = new SecureRandom();

  public VisitorSessionService(
      VisitorProfileMapper visitorProfileMapper,
      @Qualifier("csUserRpcBlockingStub")
      UserRpcGrpc.UserRpcBlockingStub userRpcStub,
      @Qualifier("csConversationRpcBlockingStub")
      ConversationRpcGrpc.ConversationRpcBlockingStub conversationRpcStub,
      SnowflakeIdGenerator idGenerator) {
    this.visitorProfileMapper = visitorProfileMapper;
    this.userRpcStub = userRpcStub;
    this.conversationRpcStub = conversationRpcStub;
    this.idGenerator = idGenerator;
  }

  /**
   * 处理访客进入 widget 的请求，幂等：同一 visitorToken 多次调用返回相同 userId 和续旧会话。
   *
   * <p>不加 @Transactional：gRPC 调用不应持有 DB 连接。只有 saveVisitorProfile() 内部有事务。
   */
  public WidgetSessionResponse enter(WidgetSessionRequest request) {
    long tenantId = TenantContext.requiredTenantId();
    String visitorToken = request.visitorToken().trim();

    // 1. 查 visitor_profile
    VisitorProfileEntity profile =
        visitorProfileMapper.findByToken(tenantId, visitorToken);

    long visitorUserId;
    String displayName;

    if (profile != null) {
      // 已有访客身份，直接复用
      visitorUserId = profile.getUserId();
      displayName   = profile.getDisplayName();
    } else {
      // 2. 新访客：生成 displayName → 创建用户 → 写 visitor_profile
      displayName = generateDisplayName();
      visitorUserId = provisionVisitorUser(tenantId, displayName);
      saveVisitorProfile(tenantId, visitorToken, visitorUserId, displayName);
    }

    // 3. 签发 JWT（每次进入都重新签发，保证 token 是新鲜的）
    IssueVisitorTokenResp tokenResp = userRpcStub.issueVisitorToken(
        IssueVisitorTokenReq.newBuilder()
            .setTenantId(tenantId)
            .setUserId(visitorUserId)
            .build());

    // 4. 查找或创建 CS 会话
    FindOrCreateCsConvResp convResp = conversationRpcStub.findOrCreateCsConv(
        FindOrCreateCsConvReq.newBuilder()
            .setTenantId(tenantId)
            .setVisitorUserId(visitorUserId)
            .build());

    if (convResp.getCode() != 0 /* OK */) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "CS 会话创建失败 code=" + convResp.getCode());
    }

    return new WidgetSessionResponse(
        tokenResp.getAccessToken(),
        tokenResp.getRefreshToken(),
        "Bearer",
        tokenResp.getExpiresIn(),
        convResp.getConvId(),
        visitorUserId,
        displayName,
        convResp.getIsNew(),
        convResp.getCsStatus()
    );
  }

  // ---- private helpers ----

  private long provisionVisitorUser(long tenantId, String displayName) {
    ProvisionVisitorUserResp resp = userRpcStub.provisionVisitorUser(
        ProvisionVisitorUserReq.newBuilder()
            .setTenantId(tenantId)
            .setDisplayName(displayName)
            .build());
    long userId = resp.getUserId();
    if (userId <= 0) {
      throw new ImException(ErrorCode.INTERNAL_ERROR, "ProvisionVisitorUser 返回无效 userId: " + userId);
    }
    return userId;
  }

  // 单条 INSERT，MyBatis 自动提交，无需显式事务。
  private void saveVisitorProfile(long tenantId, String visitorToken,
      long userId, String displayName) {
    VisitorProfileEntity profile = new VisitorProfileEntity();
    profile.setId(idGenerator.nextId());
    profile.setTenantId(tenantId);
    profile.setVisitorToken(visitorToken);
    profile.setUserId(userId);
    profile.setDisplayName(displayName);
    visitorProfileMapper.insert(profile);
  }

  /** 生成 "访客XXXX" 格式的显示名（D32）。 */
  private String generateDisplayName() {
    StringBuilder sb = new StringBuilder(DISPLAY_NAME_PREFIX);
    for (int i = 0; i < SUFFIX_LENGTH; i++) {
      sb.append(SUFFIX_CHARS.charAt(random.nextInt(SUFFIX_CHARS.length())));
    }
    return sb.toString();
  }
}
