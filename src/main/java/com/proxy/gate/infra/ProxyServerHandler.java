package com.proxy.gate.infra;

import com.proxy.gate.enums.Methods;
import com.proxy.gate.application.dtos.MatchRequestResponseDto;
import com.proxy.gate.application.dtos.RequestDto;
import com.proxy.gate.application.usecase.MatchRequestUseCase;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class ProxyServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
  private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
      "connection",
      "keep-alive",
      "proxy-authenticate",
      "proxy-authorization",
      "te",
      "trailers",
      "transfer-encoding",
      "upgrade",
      "proxy-connection");

  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
      .build();

  private final MatchRequestUseCase matchRequestUseCase;

  public ProxyServerHandler(MatchRequestUseCase matchRequestUseCase) {
    this.matchRequestUseCase = matchRequestUseCase;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest inboundRequest) {
    if (HttpMethod.CONNECT.equals(inboundRequest.method())) {
      writeError(ctx, inboundRequest, HttpResponseStatus.NOT_IMPLEMENTED, "CONNECT not supported");
      return;
    }

    URI targetUri;
    try {
      targetUri = resolveTargetUri(inboundRequest);
    } catch (IllegalArgumentException ex) {
      writeError(ctx, inboundRequest, HttpResponseStatus.BAD_REQUEST, ex.getMessage());
      return;
    }

    Optional<RequestDto> requestDto = toRequestDto(inboundRequest, targetUri);
    if (requestDto.isPresent()) {
      Optional<MatchRequestResponseDto> matchedResponse = matchRequestUseCase.execute(requestDto.get());
      if (matchedResponse.isPresent()) {
        writeMatchedResponse(ctx, inboundRequest, matchedResponse.get());
        return;
      }
    }

    byte[] requestBody = ByteBufUtil.getBytes(inboundRequest.content());

    HttpRequest.Builder outboundBuilder = HttpRequest.newBuilder(targetUri)
        .method(inboundRequest.method().name(), buildBodyPublisher(requestBody));

    copyRequestHeaders(inboundRequest.headers(), outboundBuilder);

    HTTP_CLIENT
        .sendAsync(outboundBuilder.build(), HttpResponse.BodyHandlers.ofByteArray())
        .whenComplete(
            (outboundResponse, throwable) -> {
              if (throwable != null) {
                ctx.executor()
                    .execute(
                        () -> writeError(
                            ctx,
                            inboundRequest,
                            HttpResponseStatus.BAD_GATEWAY,
                            "Unable to reach upstream"));
                return;
              }

              ctx.executor()
                  .execute(
                      () -> {
                        FullHttpResponse proxiedResponse = new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1,
                            HttpResponseStatus.valueOf(outboundResponse.statusCode()),
                            Unpooled.wrappedBuffer(outboundResponse.body()));

                        copyResponseHeaders(outboundResponse, proxiedResponse.headers());
                        proxiedResponse
                            .headers()
                            .setInt(HttpHeaderNames.CONTENT_LENGTH, proxiedResponse.content().readableBytes());

                        boolean keepAlive = HttpUtil.isKeepAlive(inboundRequest);
                        if (keepAlive) {
                          HttpUtil.setKeepAlive(proxiedResponse, true);
                          ctx.writeAndFlush(proxiedResponse);
                        } else {
                          ctx.writeAndFlush(proxiedResponse).addListener(ChannelFutureListener.CLOSE);
                        }
                      });
            });
  }

  private static Optional<RequestDto> toRequestDto(FullHttpRequest inboundRequest, URI targetUri) {
    Methods method = switch (inboundRequest.method().name()) {
      case "GET" -> Methods.GET;
      case "POST" -> Methods.POST;
      case "PUT" -> Methods.PUT;
      case "DELETE" -> Methods.DELETE;
      case "PATCH" -> Methods.PATCH;
      default -> null;
    };
    if (method == null) {
      return Optional.empty();
    }
    return Optional.of(new RequestDto(method, targetUri.toString()));
  }

  private static HttpRequest.BodyPublisher buildBodyPublisher(byte[] requestBody) {
    if (requestBody.length == 0) {
      return HttpRequest.BodyPublishers.noBody();
    }
    return HttpRequest.BodyPublishers.ofByteArray(requestBody);
  }

  private static URI resolveTargetUri(FullHttpRequest request) {
    URI uri = URI.create(request.uri());
    if (uri.isAbsolute()) {
      return uri;
    }

    String host = request.headers().get(HttpHeaderNames.HOST);
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("Missing Host header");
    }

    String normalizedPath = request.uri().startsWith("/") ? request.uri() : "/" + request.uri();
    return URI.create("http://" + host + normalizedPath);
  }

  private static void copyRequestHeaders(HttpHeaders inboundHeaders, HttpRequest.Builder outboundBuilder) {
    for (String headerName : inboundHeaders.names()) {
      if (shouldSkipHeader(headerName)) {
        continue;
      }

      for (String headerValue : inboundHeaders.getAll(headerName)) {
        outboundBuilder.header(headerName, headerValue);
      }
    }
  }

  private static void copyResponseHeaders(
      HttpResponse<byte[]> outboundResponse, HttpHeaders proxiedHeaders) {
    outboundResponse
        .headers()
        .map()
        .forEach(
            (headerName, values) -> {
              if (shouldSkipHeader(headerName)) {
                return;
              }

              for (String headerValue : values) {
                proxiedHeaders.add(headerName, headerValue);
              }
            });
  }

  private static boolean shouldSkipHeader(String headerName) {
    return HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase(Locale.ROOT))
        || HttpHeaderNames.HOST.contentEqualsIgnoreCase(headerName)
        || HttpHeaderNames.CONTENT_LENGTH.contentEqualsIgnoreCase(headerName);
  }

  private static void writeError(
      ChannelHandlerContext ctx,
      FullHttpRequest inboundRequest,
      HttpResponseStatus status,
      String message) {
    writeResponse(ctx, inboundRequest, status, message.getBytes(CharsetUtil.UTF_8), "text/plain; charset=utf-8");
  }

  private static void writeMatchedResponse(
      ChannelHandlerContext ctx,
      FullHttpRequest inboundRequest,
      MatchRequestResponseDto matchedResponse) {
    String body = matchedResponse.body() == null ? "" : matchedResponse.body();
    String contentType = matchedResponse.contentType() == null || matchedResponse.contentType().isBlank()
        ? "text/plain; charset=utf-8"
        : matchedResponse.contentType();
    writeResponse(ctx, inboundRequest, HttpResponseStatus.OK, body.getBytes(CharsetUtil.UTF_8), contentType);
  }

  private static void writeResponse(
      ChannelHandlerContext ctx,
      FullHttpRequest inboundRequest,
      HttpResponseStatus status,
      byte[] body,
      String contentType) {
    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(body));
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
    response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

    boolean keepAlive = HttpUtil.isKeepAlive(inboundRequest);
    if (keepAlive) {
      HttpUtil.setKeepAlive(response, true);
      ctx.writeAndFlush(response);
    } else {
      ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    cause.printStackTrace();
    ctx.close();
  }
}
