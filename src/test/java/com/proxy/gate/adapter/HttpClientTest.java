package com.proxy.gate.adapter;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.HashMap;

import org.junit.jupiter.api.Test;

import com.proxy.gate.enums.Methods;

public class HttpClientTest {

  @Test
  public void test200() {
    var client = new HttpClientAdapter();
    var result = client.send(URI.create("https://dummyjson.com/test"), Methods.GET, "", new HashMap<String, String>());
    assertEquals(true, result.isSuccess());
    assertEquals(200, result.getSuccess().statusCode());
  }

  @Test
  public void test404() {
    var client = new HttpClientAdapter();
    var result = client.send(URI.create("https://dummyjson.com/teste"), Methods.GET, "", new HashMap<String, String>());
    assertEquals(404, result.getSuccess().statusCode());
  }

  @Test
  public void headerCopy() {
    var client = new HttpClientAdapter();
    var headers = new HashMap<String, String>();

    headers.put("Authorization", "Bearer 1234");

    var result = client.send(URI.create("https://dummyjson.com/auth/RESOURCE"), Methods.GET, "", headers);
    assertEquals(401, result.getSuccess().statusCode());

    var pattern = Pattern.compile("invalid", Pattern.CASE_INSENSITIVE);
    var matcher = pattern.matcher(result.getSuccess().body());
    assertEquals(true, matcher.find());
  }

}
