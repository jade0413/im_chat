package com.im.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.common.error.ErrorCode;
import com.im.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantContextFilter extends OncePerRequestFilter {

  public static final String TENANT_HEADER = "X-Tenant-Id";

  private final ObjectMapper objectMapper;

  public TenantContextFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    if (!request.getRequestURI().startsWith("/api/")) {
      filterChain.doFilter(request, response);
      return;
    }

    String tenantHeader = request.getHeader(TENANT_HEADER);
    if (tenantHeader == null || tenantHeader.isBlank()) {
      writeValidationFailure(response, TENANT_HEADER + " is required");
      return;
    }

    long tenantId;
    try {
      tenantId = Long.parseLong(tenantHeader);
    } catch (NumberFormatException ex) {
      writeValidationFailure(response, TENANT_HEADER + " must be a positive integer");
      return;
    }
    if (tenantId <= 0) {
      writeValidationFailure(response, TENANT_HEADER + " must be a positive integer");
      return;
    }

    try {
      TenantContext.callWithTenant(tenantId, () -> {
        filterChain.doFilter(request, response);
        return null;
      });
    } catch (ServletException | IOException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ServletException(ex);
    }
  }

  private void writeValidationFailure(HttpServletResponse response, String message) throws IOException {
    response.setStatus(ErrorCode.VALIDATION_FAILED.httpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), ApiResponse.fail(ErrorCode.VALIDATION_FAILED, message));
  }
}
