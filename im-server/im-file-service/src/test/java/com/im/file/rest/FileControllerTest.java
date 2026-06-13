package com.im.file.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.im.common.auth.AuthTokenClaims;
import com.im.common.auth.JwtAccessTokenVerifier;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.common.web.ApiResponse;
import com.im.file.dto.PresignFileRequest;
import com.im.file.dto.PresignFileResponse;
import com.im.file.service.FileService;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

  @Mock
  private FileService fileService;

  @Mock
  private JwtAccessTokenVerifier tokenVerifier;

  @Test
  void createsPresignForAuthorizedTenantUser() throws Exception {
    PresignFileRequest request = new PresignFileRequest("a.png", "image/png", 100L, null);
    PresignFileResponse expected = new PresignFileResponse(
        1L, "1/202606/a.png", "http://upload", 1000L, Map.of("Content-Type", "image/png"));
    when(tokenVerifier.verifyAccessToken("token"))
        .thenReturn(new AuthTokenClaims(1L, 100L, Instant.now().plusSeconds(3600)));
    when(fileService.presign(100L, request)).thenReturn(expected);

    ApiResponse<PresignFileResponse> response = TenantContext.callWithTenant(1L,
        () -> new FileController(fileService, tokenVerifier).presign(request, "Bearer token"));

    assertThat(response.data()).isSameAs(expected);
  }

  @Test
  void rejectsCrossTenantToken() {
    when(tokenVerifier.verifyAccessToken("token"))
        .thenReturn(new AuthTokenClaims(2L, 100L, Instant.now().plusSeconds(3600)));

    AtomicReference<Throwable> error = new AtomicReference<>();
    TenantContext.runWithTenant(1L, () -> {
      try {
        new FileController(fileService, tokenVerifier).presign(
            new PresignFileRequest("a.png", "image/png", 100L, null), "Bearer token");
      } catch (Throwable ex) {
        error.set(ex);
      }
    });

    assertThat(error.get())
        .isInstanceOf(ImException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.TOKEN_INVALID);
  }
}
