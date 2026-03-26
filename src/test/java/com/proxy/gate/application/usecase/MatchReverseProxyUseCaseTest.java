package com.proxy.gate.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proxy.gate.adapter.HttpClientAdapter;
import com.proxy.gate.application.dtos.MatchRequestResponseDto;
import com.proxy.gate.application.dtos.RequestDto;
import com.proxy.gate.application.repository.ProxyRepository;
import com.proxy.gate.domain.ProxyMapEntity;
import com.proxy.gate.domain.objects.URIObject;
import com.proxy.gate.enums.Methods;
import com.proxy.gate.interfaces.MatchRequestUseCaseInterface;
import com.proxy.gate.utils.Either;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class MatchReverseProxyUseCaseTest {

  private static class ReverseProxyEntity extends ProxyMapEntity {
    ReverseProxyEntity(String host, boolean reverse, String reverseUri) {
      this.uri = new URIObject("https", host, "/");
      this.reverse = reverse;
      this.reverseUri = reverseUri;
    }
  }

  @Test
  public void shouldDelegateToNextWhenNoReverseProxyMatch() {
    var repository = mock(ProxyRepository.class);
    var next = mock(MatchRequestUseCaseInterface.class);
    var httpClient = mock(HttpClientAdapter.class);

    var entity = new ReverseProxyEntity("api.example.com", true, "https://upstream.internal");
    when(repository.findAll()).thenReturn(List.of(entity));

    var dto = new RequestDto(Methods.GET, URI.create("https://other.example.com/v1"), "",
        new HashMap<String, String>());
    var nextResponse = Optional.of(new MatchRequestResponseDto("fallback", "application/json", 200));
    when(next.execute(dto)).thenReturn(nextResponse);

    var useCase = new MatchReverseProxyUseCase(next, repository, httpClient);

    var result = useCase.execute(dto);

    assertEquals(nextResponse, result);
    verify(next).execute(dto);
    verify(httpClient, never()).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  public void shouldForwardToReverseProxyWhenMatchExists() {
    var repository = mock(ProxyRepository.class);
    var next = mock(MatchRequestUseCaseInterface.class);
    var httpClient = mock(HttpClientAdapter.class);

    var entity = new ReverseProxyEntity("api.example.com", true, "https://upstream.internal:8443/base");
    when(repository.findAll()).thenReturn(List.of(entity));

    var headers = new HashMap<String, String>();
    headers.put("Authorization", "Bearer token");
    var dto = new RequestDto(
        Methods.POST,
        URI.create("https://api.example.com/v1/resource?x=1#frag"),
        "payload",
        headers);

    var clientResponse = new HttpClientAdapter.HttpResponse(201, "created", "application/json");
    when(httpClient.send(
        eq(URI.create("https://upstream.internal:8443/v1/resource?x=1#frag")),
        eq(Methods.POST),
        eq("payload"),
        same(headers)))
        .thenReturn(Either.success(clientResponse));

    var useCase = new MatchReverseProxyUseCase(next, repository, httpClient);

    var result = useCase.execute(dto);

    assertTrue(result.isPresent());
    assertEquals("created", result.get().body());
    assertEquals("application/json", result.get().contentType());
    assertEquals(201, result.get().statusCode());

    verify(httpClient).send(
        URI.create("https://upstream.internal:8443/v1/resource?x=1#frag"),
        Methods.POST,
        "payload",
        headers);
    verify(next, never()).execute(dto);
  }

  @Test
  public void shouldReturnEmptyWhenReverseProxyClientReturnsError() {
    var repository = mock(ProxyRepository.class);
    var next = mock(MatchRequestUseCaseInterface.class);
    var httpClient = mock(HttpClientAdapter.class);

    var entity = new ReverseProxyEntity("api.example.com", true, "https://upstream.internal");
    when(repository.findAll()).thenReturn(List.of(entity));

    var dto = new RequestDto(Methods.GET, URI.create("https://api.example.com/v1"), "",
        new HashMap<String, String>());

    when(httpClient.send(
        eq(URI.create("https://upstream.internal/v1")),
        eq(Methods.GET),
        eq(""),
        eq(dto.headers())))
        .thenReturn(Either.error(-1));

    var useCase = new MatchReverseProxyUseCase(next, repository, httpClient);

    var result = useCase.execute(dto);

    assertFalse(result.isPresent());
    verify(next, never()).execute(dto);
  }
}
