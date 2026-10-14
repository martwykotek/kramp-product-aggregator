package com.kramp.aggregator.testing;

import com.kramp.aggregator.presentation.Labels;
import org.springframework.context.support.ResourceBundleMessageSource;

/** The application's message bundles, configured as `application.yml` configures them. */
public final class TestLabels {

  private TestLabels() {}

  public static ResourceBundleMessageSource messageSource() {
    ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
    messages.setBasename("messages");
    messages.setDefaultEncoding("UTF-8");
    // A missing translation must surface, not quietly fall back to the JVM's language.
    messages.setFallbackToSystemLocale(false);
    return messages;
  }

  public static Labels labels() {
    return new Labels(messageSource());
  }
}
