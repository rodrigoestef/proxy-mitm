package com.proxy.gate.infra;

import com.proxy.gate.application.dtos.MatchRequestResponseDto;
import com.proxy.gate.application.dtos.RequestDto;
import com.proxy.gate.application.usecase.MatchRequestUseCase;
import com.proxy.gate.enums.Methods;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ChannelHandler.Sharable
public class ProxyServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
  private static final int MAX_HTTP_CONTENT_LENGTH = 10 * 1024 * 1024;
  private static final Path WORK_DIR = Path.of(System.getProperty("java.io.tmpdir"), "gate-mitm-certs");

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

  private final MatchRequestUseCase matchRequestUseCase;
  private final HttpClient httpClient;
  private final Path caCertFile;
  private final Path caKeyFile;
  private final ConcurrentMap<String, SslContext> hostSslContexts = new ConcurrentHashMap<>();

  public ProxyServerHandler(MatchRequestUseCase matchRequestUseCase, String caCertPath, String caKeyPath) {
    this.matchRequestUseCase = matchRequestUseCase;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    try {
      Files.createDirectories(WORK_DIR);
      this.caCertFile = resolvePath(caCertPath, "ca.pem");
      this.caKeyFile = resolvePath(caKeyPath, "ca.key");
    } catch (IOException e) {
      throw new IllegalStateException("Failed to initialize certificate files", e);
    }
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest inboundRequest) {
    if (HttpMethod.CONNECT.equals(inboundRequest.method())) {
      handleConnect(ctx, inboundRequest);
      return;
    }

    forwardRequest(ctx, inboundRequest, null);
  }

  private void handleConnect(ChannelHandlerContext ctx, FullHttpRequest inboundRequest) {
    HostAndPort target;
    try {
      target = parseHostAndPort(inboundRequest.uri());
    } catch (IllegalArgumentException ex) {
      writeError(ctx, inboundRequest, HttpResponseStatus.BAD_REQUEST, ex.getMessage());
      return;
    }

    FullHttpResponse connectResponse = new DefaultFullHttpResponse(
        HttpVersion.HTTP_1_1,
        new HttpResponseStatus(200, "Connection Established"));

    ctx.writeAndFlush(connectResponse).addListener((ChannelFutureListener) future -> {
      if (!future.isSuccess()) {
        ctx.close();
        return;
      }

      try {
        SslContext sslContext = hostSslContexts.computeIfAbsent(target.host(), this::buildSslContextForHost);

        ChannelPipeline pipeline = ctx.pipeline();
        removeIfExists(pipeline, HttpServerCodec.class);
        removeIfExists(pipeline, HttpObjectAggregator.class);
        removeIfExists(pipeline, ProxyServerHandler.class);

        pipeline.addLast("mitm-ssl", sslContext.newHandler(ctx.alloc()));
        pipeline.addLast("mitm-http-codec", new HttpServerCodec());
        pipeline.addLast("mitm-http-agg", new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH));
        pipeline.addLast("https-intercept", new HttpsInterceptHandler(matchRequestUseCase, httpClient, target));
      } catch (RuntimeException ex) {
        ex.printStackTrace();
        ctx.close();
      }
    });
  }

  private void forwardRequest(ChannelHandlerContext ctx, FullHttpRequest inboundRequest, HostAndPort forcedTarget) {
    URI targetUri;
    try {
      targetUri = resolveTargetUri(inboundRequest, forcedTarget);
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

    httpClient
        .sendAsync(outboundBuilder.build(), HttpResponse.BodyHandlers.ofByteArray())
        .whenComplete((outboundResponse, throwable) -> {
          if (throwable != null) {
            ctx.executor().execute(() -> writeError(
                ctx,
                inboundRequest,
                HttpResponseStatus.BAD_GATEWAY,
                "Unable to reach upstream"));
            return;
          }

          ctx.executor().execute(() -> {
            FullHttpResponse proxiedResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(outboundResponse.statusCode()),
                Unpooled.wrappedBuffer(outboundResponse.body()));

            copyResponseHeaders(outboundResponse, proxiedResponse.headers());
            proxiedResponse.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, proxiedResponse.content().readableBytes());

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

  private SslContext buildSslContextForHost(String host) {
    try {
      HostCertificateFiles files = ensureHostCertificate(host);
      return SslContextBuilder.forServer(files.certificateFile().toFile(), files.privateKeyFile().toFile()).build();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create SSL context for host " + host, e);
    }
  }

  private synchronized HostCertificateFiles ensureHostCertificate(String host) throws IOException, InterruptedException {
    String safeHost = sanitizeHost(host);
    Path hostKey = WORK_DIR.resolve(safeHost + ".key.pem");
    Path hostCsr = WORK_DIR.resolve(safeHost + ".csr.pem");
    Path hostCrt = WORK_DIR.resolve(safeHost + ".crt.pem");
    Path hostExt = WORK_DIR.resolve(safeHost + ".ext.cnf");

    if (Files.exists(hostKey) && Files.exists(hostCrt)) {
      return new HostCertificateFiles(hostKey, hostCrt);
    }

    String san = isIpAddress(host) ? "IP:" + host : "DNS:" + host;
    Files.writeString(hostExt, "subjectAltName=" + san + "\n", StandardCharsets.UTF_8);

    runCommand(List.of("openssl", "genrsa", "-out", hostKey.toString(), "2048"));
    runCommand(List.of(
        "openssl",
        "req",
        "-new",
        "-key",
        hostKey.toString(),
        "-subj",
        "/CN=" + host,
        "-out",
        hostCsr.toString()));
    runCommand(List.of(
        "openssl",
        "x509",
        "-req",
        "-in",
        hostCsr.toString(),
        "-CA",
        caCertFile.toString(),
        "-CAkey",
        caKeyFile.toString(),
        "-CAcreateserial",
        "-out",
        hostCrt.toString(),
        "-days",
        "365",
        "-sha256",
        "-extfile",
        hostExt.toString()));

    return new HostCertificateFiles(hostKey, hostCrt);
  }

  private static synchronized Path resolvePath(String configuredPath, String fallbackFileName) throws IOException {
    if (configuredPath == null || configuredPath.isBlank()) {
      throw new IllegalArgumentException("Missing certificate path configuration");
    }

    String trimmed = configuredPath.trim();
    if (!trimmed.startsWith("classpath:")) {
      return Path.of(trimmed);
    }

    String classpathResource = trimmed.substring("classpath:".length());
    if (classpathResource.startsWith("/")) {
      classpathResource = classpathResource.substring(1);
    }

    try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathResource)) {
      if (in == null) {
        throw new IllegalArgumentException("Resource not found: " + configuredPath);
      }
      Path destination = WORK_DIR.resolve(fallbackFileName);
      if (Files.exists(destination)) {
        return destination;
      }
      Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
      return destination;
    }
  }

  private static void runCommand(List<String> command) throws IOException, InterruptedException {
    Process process = new ProcessBuilder(command)
        .redirectErrorStream(true)
        .start();

    String output;
    try (InputStream in = process.getInputStream()) {
      output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    int code = process.waitFor();
    if (code != 0) {
      throw new IllegalStateException("Command failed (" + code + "): " + String.join(" ", command) + "\n" + output);
    }
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
    return Optional.of(new RequestDto(method, targetUri));
  }

  private static HttpRequest.BodyPublisher buildBodyPublisher(byte[] requestBody) {
    if (requestBody.length == 0) {
      return HttpRequest.BodyPublishers.noBody();
    }
    return HttpRequest.BodyPublishers.ofByteArray(requestBody);
  }

  private static URI resolveTargetUri(FullHttpRequest request, HostAndPort forcedTarget) {
    URI uri = URI.create(request.uri());
    if (uri.isAbsolute()) {
      return uri;
    }

    String host;
    String scheme;

    if (forcedTarget != null) {
      host = formatAuthority(forcedTarget.host(), forcedTarget.port(), "https");
      scheme = "https";
    } else {
      host = request.headers().get(HttpHeaderNames.HOST);
      if (host == null || host.isBlank()) {
        throw new IllegalArgumentException("Missing Host header");
      }
      scheme = "http";
    }

    String normalizedPath = request.uri().startsWith("/") ? request.uri() : "/" + request.uri();
    return URI.create(scheme + "://" + host + normalizedPath);
  }

  private static String formatAuthority(String host, int port, String scheme) {
    if (host == null || host.isBlank()) {
      return "";
    }

    int defaultPort = "https".equalsIgnoreCase(scheme) ? 443 : 80;
    if (port <= 0 || port == defaultPort) {
      return host;
    }
    return host + ":" + port;
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

  private static void copyResponseHeaders(HttpResponse<byte[]> outboundResponse, HttpHeaders proxiedHeaders) {
    outboundResponse
        .headers()
        .map()
        .forEach((headerName, values) -> {
          if (shouldSkipHeader(headerName)) {
            return;
          }

          for (String headerValue : values) {
            proxiedHeaders.add(headerName, headerValue);
          }
        });
  }

  private static boolean shouldSkipHeader(String headerName) {
    return headerName.startsWith(":")
        || HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase(Locale.ROOT))
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

  private static HostAndPort parseHostAndPort(String authority) {
    if (authority == null || authority.isBlank()) {
      throw new IllegalArgumentException("Missing CONNECT authority");
    }

    String trimmed = authority.trim();

    if (trimmed.startsWith("[")) {
      int bracketEnd = trimmed.indexOf(']');
      if (bracketEnd <= 0) {
        throw new IllegalArgumentException("Invalid CONNECT host");
      }
      String host = trimmed.substring(1, bracketEnd);
      int port = 443;
      if (bracketEnd + 2 <= trimmed.length() && trimmed.charAt(bracketEnd + 1) == ':') {
        port = parsePort(trimmed.substring(bracketEnd + 2));
      }
      return new HostAndPort(host, port);
    }

    int lastColon = trimmed.lastIndexOf(':');
    if (lastColon > 0 && trimmed.indexOf(':') == lastColon) {
      return new HostAndPort(trimmed.substring(0, lastColon), parsePort(trimmed.substring(lastColon + 1)));
    }

    return new HostAndPort(trimmed, 443);
  }

  private static int parsePort(String portRaw) {
    try {
      int port = Integer.parseInt(portRaw);
      if (port <= 0 || port > 65535) {
        throw new IllegalArgumentException("Invalid CONNECT port");
      }
      return port;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Invalid CONNECT port");
    }
  }

  private static void removeIfExists(ChannelPipeline pipeline, Class<? extends ChannelHandler> handlerType) {
    ChannelHandler handler = pipeline.get(handlerType);
    if (handler != null) {
      pipeline.remove(handler);
    }
  }

  private static String sanitizeHost(String host) {
    return host.replaceAll("[^a-zA-Z0-9.-]", "_");
  }

  private static boolean isIpAddress(String host) {
    return host.matches("^\\d{1,3}(\\.\\d{1,3}){3}$") || host.contains(":");
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    cause.printStackTrace();
    ctx.close();
  }

  private record HostAndPort(String host, int port) {
  }

  private record HostCertificateFiles(Path privateKeyFile, Path certificateFile) {
  }

  private static final class HttpsInterceptHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final MatchRequestUseCase matchRequestUseCase;
    private final HttpClient httpClient;
    private final HostAndPort target;

    private HttpsInterceptHandler(
        MatchRequestUseCase matchRequestUseCase,
        HttpClient httpClient,
        HostAndPort target) {
      this.matchRequestUseCase = matchRequestUseCase;
      this.httpClient = httpClient;
      this.target = target;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest inboundRequest) {
      URI targetUri;
      try {
        targetUri = resolveTargetUri(inboundRequest, target);
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

      httpClient
          .sendAsync(outboundBuilder.build(), HttpResponse.BodyHandlers.ofByteArray())
          .whenComplete((outboundResponse, throwable) -> {
            if (throwable != null) {
              ctx.executor().execute(() -> writeError(
                  ctx,
                  inboundRequest,
                  HttpResponseStatus.BAD_GATEWAY,
                  "Unable to reach upstream"));
              return;
            }

            ctx.executor().execute(() -> {
              FullHttpResponse proxiedResponse = new DefaultFullHttpResponse(
                  HttpVersion.HTTP_1_1,
                  HttpResponseStatus.valueOf(outboundResponse.statusCode()),
                  Unpooled.wrappedBuffer(outboundResponse.body()));

              copyResponseHeaders(outboundResponse, proxiedResponse.headers());
              proxiedResponse.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, proxiedResponse.content().readableBytes());

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

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      cause.printStackTrace();
      ctx.close();
    }
  }
}
