package com.proxy.gate.application.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proxy.gate.domain.ProxyMapEntity;
import com.proxy.gate.enums.Methods;

public interface ProxyRepository extends JpaRepository<ProxyMapEntity, Integer> {
  Optional<ProxyMapEntity> findByUrlAndMethod(String url, Methods method);
}
