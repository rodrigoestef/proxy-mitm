package com.proxy.gate.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.junit.jupiter.api.Test;

import com.proxy.gate.domain.objects.URIObject;
import com.proxy.gate.enums.Methods;

public class ProxyMapEntityTest extends ProxyMapEntity {

  public ProxyMapEntityTest() {
  }

  @Test
  public void testReverseMatch(){
    var entity = new ProxyMapEntityTest();

    entity.uri = new URIObject("http", "exemple.com", "/");

    entity.method = Methods.GET;

    entity.reverse = false;

    assertEquals(false, entity.matchReverseRequest(URI.create("http://exemple.com/1234")));

    entity.reverse = true;

    assertEquals(true, entity.matchReverseRequest(URI.create("http://exemple.com/1234")));
  }

  @Test
  public void testMatchRequest() {

    var entity = new ProxyMapEntityTest();

    entity.uri = new URIObject("http", "exemple.com", "/");

    entity.method = Methods.GET;

    assertEquals(true, entity.matchRequest(URI.create("http://exemple.com/"), Methods.GET));

  }

}
