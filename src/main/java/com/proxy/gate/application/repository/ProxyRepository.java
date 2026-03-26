package com.proxy.gate.application.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proxy.gate.domain.ProxyMapEntity;

public interface ProxyRepository extends JpaRepository<ProxyMapEntity, Integer> {
}
