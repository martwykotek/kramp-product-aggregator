package com.kramp.aggregator.presentation;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/** Money as the market writes it ("57,11 zł", "£12.95"). */
public final class PriceFormatter {

  private PriceFormatter() {}

  public static String format(BigDecimal amount, Currency currency, Locale locale) {
    // NumberFormat is not thread-safe, so every call gets its own.
    NumberFormat format = NumberFormat.getCurrencyInstance(locale);
    format.setCurrency(currency);
    return format.format(amount);
  }
}
