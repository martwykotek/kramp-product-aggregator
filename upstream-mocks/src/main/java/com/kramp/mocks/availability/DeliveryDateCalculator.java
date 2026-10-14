package com.kramp.mocks.availability;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

/**
 * Kramp's promise: order before 18:00 on a working day, delivered the next working day. Later
 * orders and weekend orders are processed on the next working day. Public holidays are out of
 * scope.
 */
@Component
public class DeliveryDateCalculator {

  static final LocalTime CUTOFF = LocalTime.of(18, 0);

  private final Clock clock;

  public DeliveryDateCalculator(Clock clock) {
    this.clock = clock;
  }

  public LocalDate expectedDelivery(ZoneId marketZone, int extraWorkingDays) {
    ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(marketZone);
    LocalDate orderDay = now.toLocalDate();
    if (!isWorkingDay(orderDay) || !now.toLocalTime().isBefore(CUTOFF)) {
      orderDay = nextWorkingDay(orderDay);
    }
    LocalDate delivery = nextWorkingDay(orderDay);
    for (int i = 0; i < extraWorkingDays; i++) {
      delivery = nextWorkingDay(delivery);
    }
    return delivery;
  }

  private static boolean isWorkingDay(LocalDate day) {
    return day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY;
  }

  private static LocalDate nextWorkingDay(LocalDate day) {
    LocalDate next = day.plusDays(1);
    while (!isWorkingDay(next)) {
      next = next.plusDays(1);
    }
    return next;
  }
}
