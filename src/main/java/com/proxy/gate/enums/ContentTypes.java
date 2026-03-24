package com.proxy.gate.enums;

public enum ContentTypes {
  JSON("application/json; charset=utf-8"),
  HTML("text/html; charset=utf-8"),
  XML("application/xml; charset=utf-8");

  private final String value;

  private ContentTypes(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return this.value;
  }

}
