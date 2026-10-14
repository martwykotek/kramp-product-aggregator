package com.kramp.aggregator.testing;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.List;
import org.springframework.test.context.DynamicPropertyRegistry;

/** One upstream server for the whole test JVM, so cached Spring contexts keep a valid base URL. */
public final class SharedWireMock {

  public static final WireMockServer UPSTREAMS = start();

  private SharedWireMock() {}

  private static WireMockServer start() {
    WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
    server.start();
    return server;
  }

  public static void register(DynamicPropertyRegistry registry) {
    for (String source : List.of("catalog", "pricing", "availability", "customer")) {
      registry.add("aggregator.sources." + source + ".base-url", UPSTREAMS::baseUrl);
    }
  }
}
