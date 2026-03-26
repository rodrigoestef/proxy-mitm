package com.proxy.gate.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.junit.jupiter.api.Test;

import com.proxy.gate.enums.Methods;

public class HttpClientTest {

  @Test
  public void test200() {
    var client = new HttpClientAdapter();
    var result = client.send(URI.create("https://dummyjson.com/test"), Methods.GET, "");
    assertEquals(true, result.isSuccess());
    assertEquals(200, result.getSuccess().statusCode());
  }

  @Test
  public void test404() {
    var client = new HttpClientAdapter();
    var result = client.send(URI.create("https://dummyjson.com/teste"), Methods.GET, "");
    assertEquals(404, result.getSuccess().statusCode());
  }
}
