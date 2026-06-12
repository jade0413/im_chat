package com.im.common.web;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ImException.class)
  public ResponseEntity<ApiResponse<Void>> handleImException(ImException ex) {
    ErrorCode errorCode = ex.errorCode();
    if (errorCode.httpStatus().is5xxServerError()) {
      log.error("IM exception: code={}, message={}", errorCode.code(), ex.getMessage(), ex);
    } else {
      log.warn("IM exception: code={}, message={}", errorCode.code(), ex.getMessage());
    }
    return ResponseEntity.status(errorCode.httpStatus())
        .body(ApiResponse.fail(errorCode, ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .map(this::formatFieldError)
        .collect(Collectors.joining("; "));
    if (message.isBlank()) {
      message = ErrorCode.VALIDATION_FAILED.defaultMessage();
    }
    return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus())
        .body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED, message));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
    String message = ex.getConstraintViolations().stream()
        .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
        .collect(Collectors.joining("; "));
    if (message.isBlank()) {
      message = ErrorCode.VALIDATION_FAILED.defaultMessage();
    }
    return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus())
        .body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED, message));
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingRequestHeader(MissingRequestHeaderException ex) {
    return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus())
        .body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED,
            ex.getHeaderName() + " header is required"));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus())
        .body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED, ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
    log.error("Unexpected exception", ex);
    return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
        .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR));
  }

  private String formatFieldError(FieldError error) {
    return error.getField() + " " + error.getDefaultMessage();
  }
}
