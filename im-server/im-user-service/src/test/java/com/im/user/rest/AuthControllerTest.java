package com.im.user.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.web.GlobalExceptionHandler;
import com.im.common.web.TenantContextFilter;
import com.im.user.dto.TokenResponse;
import com.im.user.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock
  private AuthService authService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    AuthController controller = new AuthController(authService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new GlobalExceptionHandler())
        .addFilter(new TenantContextFilter(objectMapper))
        .build();
  }

  @Test
  void registerReturnsTokenResponse() throws Exception {
    when(authService.register(any())).thenReturn(tokenResponse());

    mockMvc.perform(post("/api/v1/auth/register")
            .header(TenantContextFilter.TENANT_HEADER, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"account":"alice","password":"password123","nickname":"Alice"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.accessToken").value("access-token"))
        .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"));

    verify(authService).register(any());
  }

  @Test
  void loginReturnsTokenResponse() throws Exception {
    when(authService.login(any())).thenReturn(tokenResponse());

    mockMvc.perform(post("/api/v1/auth/login")
            .header(TenantContextFilter.TENANT_HEADER, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"account":"alice","password":"password123"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.accessToken").value("access-token"));

    verify(authService).login(any());
  }

  @Test
  void refreshReturnsTokenResponse() throws Exception {
    when(authService.refresh("refresh-token")).thenReturn(tokenResponse());

    mockMvc.perform(post("/api/v1/auth/refresh")
            .header(TenantContextFilter.TENANT_HEADER, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"refreshToken":"refresh-token"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));

    verify(authService).refresh("refresh-token");
  }

  @Test
  void rejectsMissingTenantHeader() throws Exception {
    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"account":"alice","password":"password123"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(9001));
  }

  @Test
  void validatesRequestBody() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
            .header(TenantContextFilter.TENANT_HEADER, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"account":"a","password":"short"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(9001));
  }

  private TokenResponse tokenResponse() {
    return new TokenResponse("access-token", "refresh-token", "Bearer", 7200, 2_592_000);
  }
}
