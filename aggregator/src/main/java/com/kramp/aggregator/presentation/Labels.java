package com.kramp.aggregator.presentation;

import java.util.Locale;
import org.springframework.context.MessageSource;

/** Texts shown to the customer, in the market's language. A missing key fails loudly. */
public final class Labels {

  private final MessageSource messages;

  public Labels(MessageSource messages) {
    this.messages = messages;
  }

  public String text(String key, Locale locale, Object... args) {
    return messages.getMessage(key, args, locale);
  }
}
