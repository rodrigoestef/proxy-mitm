package com.proxy.gate.domain;

import com.proxy.gate.domain.objects.URIObject;
import com.proxy.gate.enums.ContentTypes;
import com.proxy.gate.enums.Methods;
import java.net.URI;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "proxy")
public class ProxyMapEntity {
  @Id
  @Column
  public int id;

  @Embedded
  protected URIObject uri;

  @Column
  @Enumerated(EnumType.STRING)
  protected Methods method;

  @Column
  public String response;

  @Column
  @Enumerated(EnumType.STRING)
  public ContentTypes content;

  @Column
  public int status;

  public boolean matchRequest(URI a, Methods method) {
    if (!this.uri.compareUri(a)) {
      return false;
    }

    if(this.method.compareTo(method) == 0){
      return true;
    }

    return false;
  }

  @Override
  public String toString() {
    return this.uri + " - " + this.method + " - " + this.content;
  }

}
