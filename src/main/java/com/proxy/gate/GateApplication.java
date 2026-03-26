package com.proxy.gate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.proxy.gate.infra.ProxyServerHandler;
import com.proxy.gate.interfaces.MatchRequestUseCaseInterface;

import io.netty.bootstrap.ServerBootstrap;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

@SpringBootApplication
public class GateApplication extends ChannelInitializer<SocketChannel> implements CommandLineRunner {

  @Autowired
  private MatchRequestUseCaseInterface matchRequestUseCase;

  @Value("${proxy.port:8080}")
  private int proxyPort;

  @Value("${proxy.ca.cert-path:classpath:ca/ca.pem}")
  private String caCertPath;

  @Value("${proxy.ca.key-path:classpath:ca/ca.key}")
  private String caKeyPath;

  private volatile ProxyServerHandler proxyServerHandler;

  public static void main(String[] args) {
    SpringApplication.run(GateApplication.class, args);
  }

  @Override
  public void run(String... args) throws Exception {
    System.out.println(System.getProperty("java.io.tmpdir"));
    EventLoopGroup bossGroup = new NioEventLoopGroup(); // (1)
    EventLoopGroup workerGroup = new NioEventLoopGroup();
    try {
      ServerBootstrap b = new ServerBootstrap(); // (2)
      b.group(bossGroup, workerGroup)
          .channel(NioServerSocketChannel.class) // (3)
          .childHandler(this)
          .option(ChannelOption.SO_BACKLOG, 128) // (5)
          .childOption(ChannelOption.SO_KEEPALIVE, true); // (6)

      // Bind and start to accept incoming connections.
      ChannelFuture f = b.bind(proxyPort).sync(); // (7)

      // Wait until the server socket is closed.
      // In this example, this does not happen, but you can do that to gracefully
      // shut down your server.
      f.channel().closeFuture().sync();
    } finally {
      workerGroup.shutdownGracefully();
      bossGroup.shutdownGracefully();
    }

  }

  @Override
  protected void initChannel(SocketChannel ch) throws Exception {
    if (proxyServerHandler == null) {
      synchronized (this) {
        if (proxyServerHandler == null) {
          proxyServerHandler = new ProxyServerHandler(this.matchRequestUseCase, caCertPath, caKeyPath);
        }
      }
    }
    ch.pipeline().addLast(new HttpServerCodec());
    ch.pipeline().addLast(new HttpObjectAggregator(10 * 1024 * 1024));
    ch.pipeline().addLast(proxyServerHandler);
  }

}
