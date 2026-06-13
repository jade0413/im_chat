package com.im.file.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.im.common.auth.UserContext;
import com.im.common.web.ApiResponse;
import com.im.file.dto.PresignFileRequest;
import com.im.file.dto.PresignFileResponse;
import com.im.file.service.FileService;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * FileController 单元测试。
 * 跨租户 / token 验证由 JwtAuthInterceptor 负责，此处只测 Controller 行为。
 */
@ExtendWith(MockitoExtension.class)
class FileControllerTest {

  @Mock
  private FileService fileService;

  @BeforeEach
  void setUp() {
    UserContext.set(100L);
  }

  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  @Test
  void createsPresignForCurrentUser() {
    PresignFileRequest request = new PresignFileRequest("a.png", "image/png", 100L, null);
    PresignFileResponse expected = new PresignFileResponse(
        1L, "1/202606/a.png", "http://upload", 1000L, Map.of("Content-Type", "image/png"));
    when(fileService.presign(100L, request)).thenReturn(expected);

    ApiResponse<PresignFileResponse> response = new FileController(fileService).presign(request);

    assertThat(response.data()).isSameAs(expected);
  }
}
