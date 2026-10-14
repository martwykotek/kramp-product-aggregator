package com.kramp.mocks.chaos;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** The demo page is served by the aggregator on :8080 and switches chaos modes here on :8081. */
@Configuration
class ChaosWebConfig implements WebMvcConfigurer {

  static final String DEMO_PAGE_ORIGIN = "http://localhost:8080";

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    // "/chaos/**" also matches "/chaos" itself (state and reset).
    registry
        .addMapping("/chaos/**")
        .allowedOrigins(DEMO_PAGE_ORIGIN)
        .allowedMethods("GET", "PUT", "DELETE");
  }
}
