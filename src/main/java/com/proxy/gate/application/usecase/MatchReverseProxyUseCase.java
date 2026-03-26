package com.proxy.gate.application.usecase;

import java.util.Optional;

import com.proxy.gate.adapter.HttpClientAdapter;
import com.proxy.gate.application.dtos.MatchRequestResponseDto;
import com.proxy.gate.application.dtos.RequestDto;
import com.proxy.gate.application.repository.ProxyRepository;
import com.proxy.gate.domain.ProxyMapEntity;
import com.proxy.gate.interfaces.MatchRequestUseCaseInterface;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Predicate;

public class MatchReverseProxyUseCase implements MatchRequestUseCaseInterface {

  private final MatchRequestUseCaseInterface next;

  private final ProxyRepository repository;

  private final HttpClientAdapter httpClient;

  public MatchReverseProxyUseCase(MatchRequestUseCaseInterface next, ProxyRepository repository,
      HttpClientAdapter httpClient) {
    this.next = next;
    this.repository = repository;
    this.httpClient = httpClient;
  }

  private static class Filter implements Predicate<ProxyMapEntity> {
    private final RequestDto dto;

    private Filter(RequestDto dto) {
      this.dto = dto;
    }

    @Override
    public boolean test(ProxyMapEntity t) {
      return t.matchReverseRequest(this.dto.url());
    }
  }

  @Override
  public Optional<MatchRequestResponseDto> execute(RequestDto dto) {

    var match = this.repository.findAll().stream().filter(new Filter(dto)).findAny();

    if (match.isEmpty()) {
      return this.next.execute(dto);
    }

    var reverseUri = match.get().getReverUri();
    var dtoUri = dto.url();
    try {

      var newURI = new URI(
          reverseUri.getScheme(),
          reverseUri.getAuthority(),
          dtoUri.getPath(),
          dtoUri.getQuery(),
          dtoUri.getFragment());

      var response = this.httpClient.send(newURI, dto.method(), dto.body(), dto.headers());

      if (response.isError()) {
        return Optional.empty();
      }

      var result = response.getSuccess();

      return Optional.of(new MatchRequestResponseDto(result.body(), result.content(), result.statusCode()));

    } catch (URISyntaxException e) {
      return Optional.empty();
    }

  }

}
