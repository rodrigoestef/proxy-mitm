package com.proxy.gate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


@Entity()
@Table(name = "teste")
public class Teste {

  @Id
  @Column
  public int id;

  @Column
  public String nome;

  @Column
  public String sobrenome;

  @Override
  public String toString() {
    return String.format("- %s - %s", this.nome, this.sobrenome);
  }

}
