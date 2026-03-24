package com.proxy.gate.application.usecase;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.proxy.gate.application.dtos.MatchRequestResponseDto;
import com.proxy.gate.application.dtos.RequestDto;
import com.proxy.gate.application.repository.ProxyRepository;

@Service
public class MatchRequestUseCase {

  private final ProxyRepository repository;

  public MatchRequestUseCase(ProxyRepository repository) {
    this.repository = repository;
  }

  public Optional<MatchRequestResponseDto> execute(RequestDto dto) {
    System.out.println(dto.method());

    var match = this.repository.findByUrlAndMethod(dto.url(), dto.method());

    if (match.isEmpty()) {
      return Optional.empty();
    }

    var entity = match.get();

    var response = new MatchRequestResponseDto(entity.response, entity.content);
    return Optional.of(response);

  }
}
