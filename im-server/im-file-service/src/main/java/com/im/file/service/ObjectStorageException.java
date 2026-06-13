package com.im.file.service;

import java.io.Serial;

public class ObjectStorageException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  private final boolean notFound;

  public ObjectStorageException(String message, Throwable cause, boolean notFound) {
    super(message, cause);
    this.notFound = notFound;
  }

  public boolean notFound() {
    return notFound;
  }
}
