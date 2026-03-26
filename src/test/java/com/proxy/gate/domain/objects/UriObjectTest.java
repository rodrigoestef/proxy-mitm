package com.proxy.gate.domain.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.net.URI;

public class UriObjectTest {

  @Test
  public void compareUriTest() {

    var a = URI.create("https://exemple.com/test?test=1");
    var object = new URIObject("https", "exemple.com", "/test?test=1");

    assertEquals(true, object.compareUri(a));

    a = URI.create("https://exemple.com/test?test=2");

    assertEquals(false, object.compareUri(a));

  }

}
