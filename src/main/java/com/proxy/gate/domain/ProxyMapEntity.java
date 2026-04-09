package com.proxy.gate.domain;

import com.proxy.gate.domain.objects.FixedResponse;
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

  @Embedded
  protected FixedResponse fixedResponse;

  @Column
  protected boolean reverse;

  @Column
  protected String reverseUri;

  public String getContentType() {
    return this.fixedResponse.getContentType();
  }

  public String getResponse() {
    return this.fixedResponse.getResponse();
  }

  public int getStatus() {
    return this.fixedResponse.getStatus();
  }

  public URI getReverUri() {
    return URI.create(this.reverseUri);
  }

  public boolean matchReverseRequest(URI a) {
    var uri = this.uri.getURI();
    if (uri.getHost().equals(a.getHost())) {
      return this.reverse;
    }
    return false;
  }

  public boolean matchRequest(URI a, Methods method) {
    if (!this.uri.compareUri(a)) {
      return false;
    }

    if (this.method.compareTo(method) == 0) {
      return true;
    }

    return false;
  }

  @Override
  public String toString() {
    return this.uri + " - " + this.method;
  }

}
