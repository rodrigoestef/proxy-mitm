package com.proxy.gate.domain;

import com.proxy.gate.enums.Methods;

import jakarta.persistence.Column;
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

  @Column
  public String url;

  @Column
  @Enumerated(EnumType.STRING)
  public Methods method;

  @Column
  public String response;

  @Column
  public String content;

}
