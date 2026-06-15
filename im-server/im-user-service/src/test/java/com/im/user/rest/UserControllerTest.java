package com.im.user.rest;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.auth.UserContext;
import com.im.common.web.GlobalExceptionHandler;
import com.im.common.web.TenantContextFilter;
import com.im.user.dto.UserProfileResponse;
import com.im.user.service.AgentService;
import com.im.user.service.AuthService;
import com.im.user.service.UsernameService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserControllerTest {

  @Mock
  private AuthService authService;

  @Mock
  private AgentService agentService;

  @Mock
  private UsernameService usernameService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    UserController controller = new UserController(authService, agentService, usernameService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new GlobalExceptionHandler())
        .addFilter(new TenantContextFilter(new ObjectMapper()))
        .build();
  }

  @Test
  void meReturnsCurrentUser() throws Exception {
    // 模拟 JwtAuthInterceptor 已写入 UserContext（standalone MockMvc 不挂该拦截器）
    UserContext.set(101L);
    try {
      when(authService.getProfile(101L)).thenReturn(new UserProfileResponse(
          101L, 1L, "alice", "Alice", "", 1, 0, 1, LocalDateTime.parse("2026-06-13T00:00:00"),
          "alice_im", 1, false, 0));

      mockMvc.perform(get("/api/v1/users/me")
              .header(TenantContextFilter.TENANT_HEADER, "1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(0))
          .andExpect(jsonPath("$.data.id").value(101))
          .andExpect(jsonPath("$.data.tenantId").value(1))
          .andExpect(jsonPath("$.data.account").value("alice"));

      verify(authService).getProfile(101L);
    } finally {
      UserContext.clear();
    }
  }

  @Test
  void rejectsWhenUnauthenticated() throws Exception {
    // 未设置 UserContext（无有效鉴权）→ UserContext.requiredUserId() 抛 TOKEN_INVALID → 401
    mockMvc.perform(get("/api/v1/users/me")
            .header(TenantContextFilter.TENANT_HEADER, "1"))
        .andExpect(status().isUnauthorized());
  }
}
