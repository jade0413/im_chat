package com.im.file.rest;

import com.im.common.auth.UserContext;
import com.im.common.web.ApiResponse;
import com.im.file.dto.ConfirmFileRequest;
import com.im.file.dto.DownloadFileResponse;
import com.im.file.dto.FileMetaResponse;
import com.im.file.dto.PresignFileRequest;
import com.im.file.dto.PresignFileResponse;
import com.im.file.service.FileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

  private final FileService fileService;

  public FileController(FileService fileService) {
    this.fileService = fileService;
  }

  @PostMapping("/presign")
  public ApiResponse<PresignFileResponse> presign(@RequestBody PresignFileRequest request) {
    return ApiResponse.ok(fileService.presign(UserContext.requiredUserId(), request));
  }

  @PostMapping("/confirm")
  public ApiResponse<FileMetaResponse> confirm(@RequestBody ConfirmFileRequest request) {
    return ApiResponse.ok(fileService.confirm(UserContext.requiredUserId(), request));
  }

  @GetMapping("/download")
  public ApiResponse<String> download(@RequestParam("key") String objectKey,
      @RequestParam(value = "variant", required = false) String variant) {
    return ApiResponse.ok(fileService.presignDownload(UserContext.requiredUserId(), objectKey, variant));
  }

  @GetMapping("/download-info")
  public ApiResponse<DownloadFileResponse> downloadInfo(@RequestParam("key") String objectKey,
      @RequestParam(value = "variant", required = false) String variant) {
    return ApiResponse.ok(fileService.presignDownloadInfo(UserContext.requiredUserId(), objectKey, variant));
  }
}
