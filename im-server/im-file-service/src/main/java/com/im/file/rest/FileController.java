package com.im.file.rest;

import com.im.common.auth.AuthTokenClaims;
import com.im.common.auth.BearerTokenExtractor;
import com.im.common.auth.JwtAccessTokenVerifier;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.common.web.ApiResponse;
import com.im.file.dto.ConfirmFileRequest;
import com.im.file.dto.FileMetaResponse;
import com.im.file.dto.PresignFileRequest;
import com.im.file.dto.PresignFileResponse;
import com.im.file.service.FileService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

  private final FileService fileService;
  private final JwtAccessTokenVerifier tokenVerifier;

  public FileController(FileService fileService, JwtAccessTokenVerifier tokenVerifier) {
    this.fileService = fileService;
    this.tokenVerifier = tokenVerifier;
  }

  @PostMapping("/presign")
  public ApiResponse<PresignFileResponse> presign(
      @RequestBody PresignFileRequest request,
      @RequestHeader("Authorization") String authorization) {
    AuthTokenClaims claims = verifiedClaims(authorization);
    return ApiResponse.ok(fileService.presign(claims.userId(), request));
  }

  @PostMapping("/confirm")
  public ApiResponse<FileMetaResponse> confirm(
      @RequestBody ConfirmFileRequest request,
      @RequestHeader("Authorization") String authorization) {
    AuthTokenClaims claims = verifiedClaims(authorization);
    return ApiResponse.ok(fileService.confirm(claims.userId(), request));
  }

  private AuthTokenClaims verifiedClaims(String authorization) {
    AuthTokenClaims claims = tokenVerifier.verifyAccessToken(BearerTokenExtractor.extract(authorization));
    if (claims.tenantId() != TenantContext.requiredTenantId()) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    return claims;
  }
}
