package com.kramp.mocks.errors;

import com.kramp.mocks.behavior.Upstream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Each upstream is owned by a different team and reports errors in its own format; the aggregator
 * has to normalise them. Messages are fixed strings, so plain formatting is safe.
 */
public final class ErrorShapes {

  private ErrorShapes() {}

  public static ResponseEntity<String> response(
      Upstream upstream, HttpStatus status, String code, String message) {
    return switch (upstream) {
      case CATALOG ->
          json(status, "{\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}".formatted(code, message));
      case PRICING ->
          json(status, "{\"status\":%d,\"reason\":\"%s\"}".formatted(status.value(), message));
      case AVAILABILITY ->
          json(
              status,
              "{\"errorCode\":\"AV-%d\",\"detail\":\"%s\"}".formatted(status.value(), message));
      case CUSTOMER ->
          ResponseEntity.status(status)
              .contentType(MediaType.TEXT_PLAIN)
              .body(status.getReasonPhrase());
    };
  }

  public static void write(
      Upstream upstream, HttpStatus status, String code, String message, HttpServletResponse out)
      throws IOException {
    ResponseEntity<String> entity = response(upstream, status, code, message);
    out.setStatus(status.value());
    out.setContentType(String.valueOf(entity.getHeaders().getContentType()));
    out.setCharacterEncoding("UTF-8");
    out.getWriter().write(entity.getBody());
  }

  private static ResponseEntity<String> json(HttpStatus status, String body) {
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
  }
}
