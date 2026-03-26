package com.proxy.gate.adapter;

import com.proxy.gate.utils.Either;
import java.util.function.Consumer;
import com.proxy.gate.enums.Methods;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;

import java.util.Map;
import java.util.Map.Entry;

public class HttpClientAdapter {

  private final HttpClient httpClient;

  public record HttpResponse(int statusCode, String body) {
  }

  private static class CopyHeaders implements Consumer<Map.Entry<String, String>> {
    private final HttpRequest.Builder builder;

    private CopyHeaders(HttpRequest.Builder builder) {
      this.builder = builder;
    }

    @Override
    public void accept(Entry<String, String> t) {
      this.builder.header(t.getKey(), t.getValue());
    }
  }

  public HttpClientAdapter() {
    this.httpClient = HttpClient.newHttpClient();
  }

  public Either<Integer, HttpResponse> send(URI uri, Methods method, String body, Map<String, String> headers) {
    var requestBuilder = HttpRequest.newBuilder().uri(uri).method(method.name(), BodyPublishers.ofString(body));

    headers.entrySet().forEach(new CopyHeaders(requestBuilder));

    var request = requestBuilder.build();

    try {
      var response = this.httpClient.send(request, BodyHandlers.ofString());

      return Either.success(new HttpResponse(response.statusCode(), response.body()));
    } catch (Exception e) {
      return Either.error(-1);
    }
  }

}
