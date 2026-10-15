package com.kramp.aggregator.testing;

import com.kramp.aggregator.source.UpstreamHttp;
import java.net.URI;
import java.time.Duration;
import org.springframework.web.client.RestClient;

/** An upstream client for L2 tests: real HTTP, no Spring context. */
public final class UpstreamClients {

  /**
   * Read timeout for tests about what a source sends and how it reads the answer. They are not
   * about time, and a cold first HTTP call on a CI runner can take longer than a 200–350 ms budget.
   */
  static final Duration CONTENT_TEST_TIMEOUT = Duration.ofSeconds(5);

  private UpstreamClients() {}

  /** The source's spec budget as the read timeout: for tests about timing. */
  public static RestClient client(String baseUrl, String source) {
    return UpstreamHttp.client(
        RestClient.builder(), URI.create(baseUrl), TestProperties.policy(source).timeout());
  }

  /** A generous read timeout: for tests about request and response content. */
  public static RestClient contentClient(String baseUrl) {
    return UpstreamHttp.client(RestClient.builder(), URI.create(baseUrl), CONTENT_TEST_TIMEOUT);
  }
}
