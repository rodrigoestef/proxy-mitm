package com.proxy.gate.application.usecase;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.proxy.gate.application.dtos.MatchRequestResponseDto;
import com.proxy.gate.application.dtos.RequestDto;
import com.proxy.gate.application.repository.ProxyRepository;
import com.proxy.gate.domain.ProxyMapEntity;

import java.util.function.Predicate;

@Service
public class MatchRequestUseCase {

  private final ProxyRepository repository;

  private static class Filter implements Predicate<ProxyMapEntity> {

    private final RequestDto dto;

    private Filter(RequestDto dto) {
      this.dto = dto;
    }

    @Override
    public boolean test(ProxyMapEntity t) {
      return t.matchRequest(this.dto.url(), this.dto.method());
    }
  }

  public MatchRequestUseCase(ProxyRepository repository) {
    this.repository = repository;
  }

  public Optional<MatchRequestResponseDto> execute(RequestDto dto) {

    var match = this.repository.findAll().stream().filter(new Filter(dto)).findAny();

    // System.out.println(dto.url());
    // System.out.println(dto.body());

    if (match.isEmpty()) {
      return Optional.empty();
    }

    var entity = match.get();
    System.out.println(entity);

    var response = new MatchRequestResponseDto(entity.response, entity.content.toString(), entity.status);
    return Optional.of(response);

  }
}
