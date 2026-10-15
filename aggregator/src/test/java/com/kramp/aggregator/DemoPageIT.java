package com.kramp.aggregator;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;

// A real server, because MockMvc does not follow the forward from "/" to index.html.
// A free gRPC port: the gRPC server starts with every context, and 9090 may be taken (docker
// compose). With both servers on random ports @LocalServerPort received the gRPC port, so the
// servlet container's port is read from the web server itself.
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "spring.grpc.server.port=0")
class DemoPageIT {

  static final String FOOTER =
      "Recruitment assignment demo. Product data from kramp.com; prices, stock and customers are"
          + " fictitious.";

  @Autowired ServletWebServerApplicationContext context;

  @Nested
  class Root {

    @Test
    void shouldServeDemoPageWhenRootRequested() throws Exception {
      // Given
      HttpRequest request =
          HttpRequest.newBuilder(
                  URI.create("http://localhost:" + context.getWebServer().getPort() + "/"))
              .build();

      // When
      HttpResponse<String> response;
      try (HttpClient client = HttpClient.newHttpClient()) {
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
      }

      // Then
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("Content-Type"))
          .hasValueSatisfying(type -> assertThat(type).startsWith("text/html"));
      assertThat(response.body())
          .contains(
              "KRAMP",
              FOOTER,
              "Validation scenarios",
              "Technical details",
              "Produkt chwilowo niedostępny");
    }
  }
}
