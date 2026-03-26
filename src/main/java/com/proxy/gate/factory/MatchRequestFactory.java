package com.proxy.gate.factory;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import com.proxy.gate.adapter.HttpClientAdapter;
import com.proxy.gate.application.repository.ProxyRepository;
import com.proxy.gate.application.usecase.MatchRequestUseCase;
import com.proxy.gate.application.usecase.MatchReverseProxyUseCase;
import com.proxy.gate.interfaces.MatchRequestUseCaseInterface;

@Service
public class MatchRequestFactory {

  private final ProxyRepository proxyRepository;

  private final HttpClientAdapter httpClient;

  public MatchRequestFactory(ProxyRepository proxyRepository, HttpClientAdapter httpClient) {
    this.proxyRepository = proxyRepository;
    this.httpClient = httpClient;
  }

  @Bean
  public MatchRequestUseCaseInterface create() {
    return new MatchReverseProxyUseCase(new MatchRequestUseCase(this.proxyRepository), this.proxyRepository, this.httpClient);
  }
}
