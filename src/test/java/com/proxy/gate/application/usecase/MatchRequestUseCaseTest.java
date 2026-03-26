package com.proxy.gate.application.usecase;

import com.proxy.gate.application.dtos.RequestDto;

import org.junit.jupiter.api.Test;

import com.proxy.gate.application.repository.ProxyRepository;
import com.proxy.gate.domain.ProxyMapEntity;
import com.proxy.gate.domain.objects.URIObject;
import com.proxy.gate.enums.ContentTypes;
import com.proxy.gate.enums.Methods;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class MatchRequestUseCaseTest extends ProxyMapEntity {

  public MatchRequestUseCaseTest() {
    this.content = ContentTypes.XML;
    this.method = Methods.GET;
  }

  @Test
  public void testUnmatchByURI() {
    var repo = mock(ProxyRepository.class);

    var en = new MatchRequestUseCaseTest();
    en.uri = new URIObject("https","exemple.com.br", "/");
    when(repo.findAll()).thenReturn(List.of(en));

    var service = new MatchRequestUseCase(repo);

    var dto = new RequestDto(Methods.GET, URI.create("https://exemple.com/"));

    var result = service.execute(dto);

    assertEquals(true, result.isEmpty());
  }

  @Test
  public void testUnmatchByMethod() {
    var repo = mock(ProxyRepository.class);

    var en = new MatchRequestUseCaseTest();
    en.uri = new URIObject("https","exemple.com", "/");
    when(repo.findAll()).thenReturn(List.of(en));

    var service = new MatchRequestUseCase(repo);

    var dto = new RequestDto(Methods.POST, URI.create("https://exemple.com/"));

    var result = service.execute(dto);

    assertEquals(true, result.isEmpty());
  }


  @Test
  public void testMatch() {
    var repo = mock(ProxyRepository.class);

    var en = new MatchRequestUseCaseTest();
    en.uri = new URIObject("https","exemple.com", "/");
    when(repo.findAll()).thenReturn(List.of(en));

    var service = new MatchRequestUseCase(repo);

    var dto = new RequestDto(Methods.GET, URI.create("https://exemple.com/"));

    var result = service.execute(dto);

    assertEquals(false, result.isEmpty());
  }

}
