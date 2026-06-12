package com.im.user.rest;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.web.GlobalExceptionHandler;
import com.im.common.web.TenantContextFilter;
import com.im.user.dto.UserProfileResponse;
import com.im.user.service.AuthService;
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

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    UserController controller = new UserController(authService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new GlobalExceptionHandler())
        .addFilter(new TenantContextFilter(new ObjectMapper()))
        .build();
  }

  @Test
  void meReturnsCurrentUser() throws Exception {
    when(authService.currentUser("access-token")).thenReturn(new UserProfileResponse(
        101L, 1L, "alice", "Alice", "", 1, 0, 1, LocalDateTime.parse("2026-06-13T00:00:00")));

    mockMvc.perform(get("/api/v1/users/me")
            .header(TenantContextFilter.TENANT_HEADER, "1")
            .header("Authorization", "Bearer access-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.id").value(101))
        .andExpect(jsonPath("$.data.tenantId").value(1))
        .andExpect(jsonPath("$.data.account").value("alice"));

    verify(authService).currentUser("access-token");
  }

  @Test
  void rejectsMissingAuthorizationHeader() throws Exception {
    mockMvc.perform(get("/api/v1/users/me")
            .header(TenantContextFilter.TENANT_HEADER, "1"))
        .andExpect(status().isBadRequest());
  }
}
