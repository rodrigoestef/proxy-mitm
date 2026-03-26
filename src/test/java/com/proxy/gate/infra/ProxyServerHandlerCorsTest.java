package com.proxy.gate.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.proxy.gate.application.dtos.MatchRequestResponseDto;
import com.proxy.gate.interfaces.MatchRequestUseCaseInterface;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class ProxyServerHandlerCorsTest {

  @Test
  public void shouldHandlePreflightLocallyWithCorsHeaders() {
    var useCase = mock(MatchRequestUseCaseInterface.class);
    var handler = new ProxyServerHandler(useCase, "classpath:ca/ca.pem", "classpath:ca/ca.key");
    var channel = new EmbeddedChannel(handler);

    var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.OPTIONS, "/v1/test");
    request.headers().set(HttpHeaderNames.ORIGIN, "http://localhost:3000");
    request.headers().set(HttpHeaderNames.HOST, "api.example.com");
    request.headers().set(HttpHeaderNames.ACCESS_CONTROL_REQUEST_HEADERS, "x-custom, authorization");

    channel.writeInbound(request);

    FullHttpResponse response = waitForOutbound(channel);
    assertNotNull(response);
    assertEquals(HttpResponseStatus.NO_CONTENT, response.status());
    assertEquals("*", response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN));
    assertEquals("GET, POST, PUT, PATCH, DELETE, OPTIONS",
        response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS));
    assertEquals("x-custom, authorization", response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS));
    assertEquals("600", response.headers().get(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE));
    assertEquals(0, response.content().readableBytes());

    response.release();
    channel.finishAndReleaseAll();
  }

  @Test
  public void shouldAddCorsHeadersToMatchedResponses() {
    var useCase = mock(MatchRequestUseCaseInterface.class);
    when(useCase.execute(any())).thenReturn(Optional.of(new MatchRequestResponseDto("ok", "application/json", 200)));

    var handler = new ProxyServerHandler(useCase, "classpath:ca/ca.pem", "classpath:ca/ca.key");
    var channel = new EmbeddedChannel(handler);

    var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://api.example.com/v1/test");
    request.headers().set(HttpHeaderNames.ORIGIN, "http://localhost:3000");

    channel.writeInbound(request);

    FullHttpResponse response = waitForOutbound(channel);
    assertNotNull(response);
    assertEquals(HttpResponseStatus.OK, response.status());
    assertEquals("*", response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN));
    assertEquals("GET, POST, PUT, PATCH, DELETE, OPTIONS",
        response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS));
    assertEquals("Content-Type, Authorization", response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS));

    response.release();
    channel.finishAndReleaseAll();
  }

  @Test
  public void shouldAddCorsHeadersToLocalErrorResponses() {
    var useCase = mock(MatchRequestUseCaseInterface.class);
    when(useCase.execute(any())).thenReturn(Optional.empty());

    var handler = new ProxyServerHandler(useCase, "classpath:ca/ca.pem", "classpath:ca/ca.key");
    var channel = new EmbeddedChannel(handler);

    var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/v1/needs-host");
    request.headers().set(HttpHeaderNames.ORIGIN, "http://localhost:3000");

    channel.writeInbound(request);

    FullHttpResponse response = waitForOutbound(channel);
    assertNotNull(response);
    assertEquals(HttpResponseStatus.BAD_REQUEST, response.status());
    assertEquals("*", response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN));
    assertEquals("GET, POST, PUT, PATCH, DELETE, OPTIONS",
        response.headers().get(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS));

    response.release();
    channel.finishAndReleaseAll();
  }

  private static FullHttpResponse waitForOutbound(EmbeddedChannel channel) {
    for (int i = 0; i < 25; i++) {
      channel.runPendingTasks();
      channel.runScheduledPendingTasks();
      Object outbound = channel.readOutbound();
      if (outbound instanceof FullHttpResponse response) {
        return response;
      }

      try {
        TimeUnit.MILLISECONDS.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }

    return null;
  }
}
