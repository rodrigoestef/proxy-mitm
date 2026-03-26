package com.proxy.gate.adapter;

import com.proxy.gate.utils.Either;
import com.proxy.gate.enums.Methods;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;

public class HttpClientAdapter {

  private final HttpClient httpClient;

  public record HttpResponse(int statusCode, String body){}

  public HttpClientAdapter() {
    this.httpClient = HttpClient.newHttpClient();
  }

  public Either<Integer, HttpResponse> send(URI uri, Methods method, String body) {
    var request = HttpRequest.newBuilder().uri(uri).method(method.name(), BodyPublishers.ofString(body)).build();

    try {
      var response = this.httpClient.send(request, BodyHandlers.ofString());

      return Either.success(new HttpResponse(response.statusCode(), response.body()));
    } catch (Exception e) {
      return Either.error(-1);
    }
  }

}
