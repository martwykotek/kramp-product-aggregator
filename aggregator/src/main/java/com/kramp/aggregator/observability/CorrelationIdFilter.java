package com.kramp.aggregator.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs first, so every log line and every answer of the request, errors included, carry the id. The
 * header is set before the chain because a committed response can no longer take it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class CorrelationIdFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String correlationId = CorrelationId.acceptOrGenerate(request.getHeader(CorrelationId.HEADER));
    MDC.put(CorrelationId.MDC_KEY, correlationId);
    response.setHeader(CorrelationId.HEADER, correlationId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(CorrelationId.MDC_KEY);
    }
  }
}
