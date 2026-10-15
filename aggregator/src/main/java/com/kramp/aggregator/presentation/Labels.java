package com.kramp.aggregator.presentation;

import java.util.Locale;
import org.springframework.context.MessageSource;

/** Texts shown to the customer, in the market's language. A missing key fails loudly. */
public final class Labels {

  /**
   * Each bundle names its own language. The lookup quietly falls back to the base (English) bundle
   * for a language without one, and this key is how that fallback shows.
   */
  private static final String BUNDLE_LANGUAGE = "bundle.language";

  private final MessageSource messages;

  public Labels(MessageSource messages) {
    this.messages = messages;
  }

  public String text(String key, Locale locale, Object... args) {
    return messages.getMessage(key, args, locale);
  }

  /** Whether texts for this locale come from a bundle in its own language. */
  public boolean hasBundleFor(Locale locale) {
    return locale.getLanguage().equals(messages.getMessage(BUNDLE_LANGUAGE, null, locale));
  }
}
