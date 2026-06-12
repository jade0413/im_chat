package com.im.common.web;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @AfterEach
  void tearDown() {
    TraceContext.clear();
  }

  @Test
  void rendersImExceptionAsStructuredResponse() throws Exception {
    TraceContext.setTraceId("trace-im");

    mockMvc.perform(get("/test/im-exception"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_INVALID.code()))
        .andExpect(jsonPath("$.message").value("bad token"))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(jsonPath("$.traceId").value("trace-im"));
  }

  @Test
  void rendersIllegalArgumentAsValidationFailure() throws Exception {
    mockMvc.perform(get("/test/illegal-argument"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
        .andExpect(jsonPath("$.message").value("bad input"))
        .andExpect(jsonPath("$.traceId").value(not(nullValue())));
  }

  @Test
  void hidesUnexpectedExceptionDetails() throws Exception {
    mockMvc.perform(get("/test/unexpected"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR.code()))
        .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.defaultMessage()))
        .andExpect(jsonPath("$.traceId").value(not(nullValue())));
  }

  @RestController
  static class TestController {

    @GetMapping("/test/im-exception")
    String imException() {
      throw new ImException(ErrorCode.TOKEN_INVALID, "bad token");
    }

    @GetMapping("/test/illegal-argument")
    String illegalArgument() {
      throw new IllegalArgumentException("bad input");
    }

    @GetMapping("/test/unexpected")
    String unexpected() {
      throw new IllegalStateException("boom");
    }
  }
}
