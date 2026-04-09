package com.proxy.gate.domain.objects;

import com.proxy.gate.enums.ContentTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class FixedResponse {

  protected String response;

  @Column
  @Enumerated(EnumType.STRING)
  protected ContentTypes content;

  @Column
  protected int status;

  public String getResponse() {
    return this.response;
  }

  public int getStatus() {
    return this.status;
  }

  public String getContentType() {
    return this.content.toString();
  }
  
}
