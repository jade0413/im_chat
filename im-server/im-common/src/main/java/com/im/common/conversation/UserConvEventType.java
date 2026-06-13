package com.im.common.conversation;

public enum UserConvEventType {
  CREATED("created"),
  UPDATED("updated"),
  REMOVED("removed");

  private final String value;

  UserConvEventType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
