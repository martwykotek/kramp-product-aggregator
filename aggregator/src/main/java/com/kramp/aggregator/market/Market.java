package com.kramp.aggregator.market;

import java.util.Currency;
import java.util.Locale;

/** A configured market: its code (`pl-PL`), the locale that code names, and its currency. */
public record Market(String code, Locale locale, Currency currency) {

  public static Market of(String code, Currency currency) {
    return new Market(code, Locale.forLanguageTag(code), currency);
  }

  public String language() {
    return locale.getLanguage();
  }
}
