package com.kramp.aggregator.source;

import com.kramp.aggregator.aggregation.UpstreamException;
import com.kramp.aggregator.aggregation.UpstreamTimeoutException;
import com.kramp.aggregator.aggregation.UpstreamUnavailableException;
import com.kramp.aggregator.observability.CorrelationId;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Builds an upstream client and turns every client-side failure into one of our three upstream
 * exceptions. Messages name the source and the status only: client messages carry the URL (with the
 * customer id) and the upstream's body.
 */
public final class UpstreamHttp {

  private UpstreamHttp() {}

  public static RestClient client(RestClient.Builder builder, URI baseUrl, Duration timeout) {
    HttpClient http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(timeout)
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(timeout);
    return builder
        .clone()
        .baseUrl(baseUrl.toString())
        .requestFactory(factory)
        .requestInterceptor(UpstreamHttp::forwardCorrelationId)
        .requestInterceptor(bodyWithin(timeout))
        .build();
  }

  public static <T> T fetch(String source, Supplier<T> exchange) {
    T body;
    try {
      body = exchange.get();
    } catch (UpstreamException alreadyTranslated) {
      throw alreadyTranslated;
    } catch (HttpServerErrorException e) {
      throw new UpstreamUnavailableException(
          source, source + " answered " + e.getStatusCode().value(), true);
    } catch (HttpClientErrorException e) {
      // 404 is translated by each source; any other 4xx means we cannot use this answer.
      throw new UpstreamUnavailableException(
          source, source + " answered " + e.getStatusCode().value(), false);
    } catch (ResourceAccessException e) {
      if (causedBy(e, HttpTimeoutException.class)) {
        throw new UpstreamTimeoutException(source, source + " did not answer within its budget");
      }
      // Not ours to retry: the JDK client already resent the GET once when the connection
      // failed before any answer, so a second attempt here would make it four calls.
      throw new UpstreamUnavailableException(source, source + " connection failed", false);
    } catch (RestClientException e) {
      throw new UpstreamUnavailableException(source, source + " sent an unreadable body", false);
    }
    if (body == null) {
      throw new UpstreamUnavailableException(source, source + " sent an empty body", false);
    }
    return body;
  }

  /** The answer was readable but breaks the upstream's contract. */
  public static UpstreamUnavailableException invalidBody(String source) {
    return new UpstreamUnavailableException(source, source + " sent an invalid body", false);
  }

  private static ClientHttpResponse forwardCorrelationId(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    String correlationId = CorrelationId.current();
    if (correlationId != null) {
      request.getHeaders().set(CorrelationId.HEADER, correlationId);
    }
    return execution.execute(request, body);
  }

  /**
   * Reads the whole body inside the exchange. When the read timeout fires while a body is still
   * arriving, Spring's JDK client only closes the stream, so the read fails as "closed" and the
   * timeout would pass for a broken body. A read that fails once the budget is spent is the
   * timeout.
   */
  private static ClientHttpRequestInterceptor bodyWithin(Duration timeout) {
    return (request, body, execution) -> {
      long start = System.nanoTime();
      ClientHttpResponse response = execution.execute(request, body);
      try {
        return new BufferedResponse(response, response.getBody().readAllBytes());
      } catch (IOException readFailure) {
        response.close();
        if (System.nanoTime() - start >= timeout.toNanos()) {
          HttpTimeoutException timedOut =
              new HttpTimeoutException("body not received within " + timeout.toMillis() + " ms");
          timedOut.initCause(readFailure);
          throw timedOut;
        }
        throw readFailure;
      }
    };
  }

  private static final class BufferedResponse implements ClientHttpResponse {

    private final ClientHttpResponse response;
    private final byte[] body;

    BufferedResponse(ClientHttpResponse response, byte[] body) {
      this.response = response;
      this.body = body;
    }

    @Override
    public HttpStatusCode getStatusCode() throws IOException {
      return response.getStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
      return response.getStatusText();
    }

    @Override
    public HttpHeaders getHeaders() {
      return response.getHeaders();
    }

    @Override
    public InputStream getBody() {
      return new ByteArrayInputStream(body);
    }

    @Override
    public void close() {
      response.close();
    }
  }

  private static boolean causedBy(Throwable failure, Class<? extends Throwable> type) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (type.isInstance(cause)) {
        return true;
      }
    }
    return false;
  }
}
