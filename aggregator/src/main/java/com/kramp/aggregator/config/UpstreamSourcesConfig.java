package com.kramp.aggregator.config;

import com.kramp.aggregator.source.UpstreamHttp;
import com.kramp.aggregator.source.availability.AvailabilitySource;
import com.kramp.aggregator.source.catalog.CatalogSource;
import com.kramp.aggregator.source.customer.CustomerSource;
import com.kramp.aggregator.source.pricing.PricingSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** The four Kramp upstreams, each with a client bound to its own address and budget. */
@Configuration(proxyBeanMethods = false)
class UpstreamSourcesConfig {

  @Bean
  CatalogSource catalogSource(RestClient.Builder builder, AggregatorProperties properties) {
    return new CatalogSource(client(builder, properties, CatalogSource.NAME));
  }

  @Bean
  PricingSource pricingSource(RestClient.Builder builder, AggregatorProperties properties) {
    return new PricingSource(client(builder, properties, PricingSource.NAME));
  }

  @Bean
  AvailabilitySource availabilitySource(
      RestClient.Builder builder, AggregatorProperties properties) {
    return new AvailabilitySource(client(builder, properties, AvailabilitySource.NAME));
  }

  @Bean
  CustomerSource customerSource(RestClient.Builder builder, AggregatorProperties properties) {
    return new CustomerSource(client(builder, properties, CustomerSource.NAME));
  }

  private static RestClient client(
      RestClient.Builder builder, AggregatorProperties properties, String source) {
    AggregatorProperties.SourceSettings settings = properties.sources().get(source);
    if (settings == null) {
      throw new IllegalStateException("aggregator.sources." + source + " is not configured");
    }
    return UpstreamHttp.client(builder, settings.baseUrl(), settings.timeout());
  }
}
