package com.proxy.gate.domain.objects;

import java.net.URI;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class URIObject {
  @Column()
  protected String scheme;

  @Column()
  protected String host;

  @Column()
  private String path;

  public URIObject() {
  }

  public URIObject(String scheme, String host, String path) {
    this.scheme = scheme;
    this.host = host;
    this.path = path;
  }

  public boolean compareUri(URI s) {
    return this.getURI().compareTo(s) == 0 ? true : false;
  }

  public URI getURI() {
    return URI.create(this.scheme + "://" + this.host + this.path);
  }

  @Override
  public String toString() {
    return this.getURI().toString();
  }
}
