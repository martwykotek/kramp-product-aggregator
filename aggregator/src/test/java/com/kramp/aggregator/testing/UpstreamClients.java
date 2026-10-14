package com.kramp.aggregator.testing;

import com.kramp.aggregator.source.UpstreamHttp;
import java.net.URI;
import org.springframework.web.client.RestClient;

/** An upstream client for L2 tests: real HTTP, the source's spec budget, no Spring context. */
public final class UpstreamClients {

  private UpstreamClients() {}

  public static RestClient client(String baseUrl, String source) {
    return UpstreamHttp.client(
        RestClient.builder(), URI.create(baseUrl), TestProperties.policy(source).timeout());
  }
}
