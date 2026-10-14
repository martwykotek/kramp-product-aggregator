package com.kramp.mocks.behavior;

import com.kramp.mocks.errors.ErrorShapes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies the simulated latency and failures before an upstream controller runs. Requests run on
 * virtual threads, so sleeping here costs no platform thread.
 */
@Component
class UpstreamBehaviorFilter extends OncePerRequestFilter {

  private final UpstreamBehavior behavior;

  UpstreamBehaviorFilter(UpstreamBehavior behavior) {
    this.behavior = behavior;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Optional<Upstream> upstream = Upstream.fromPath(request.getRequestURI());
    if (upstream.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }
    Decision decision = behavior.decide(upstream.get());
    try {
      Thread.sleep(decision.delay());
    } catch (InterruptedException e) {
      // Returning here would send an empty 200, which a client would take for a valid answer.
      Thread.currentThread().interrupt();
      throw new ServletException("Interrupted while simulating upstream latency", e);
    }
    switch (decision) {
      case Decision.Succeed succeed -> chain.doFilter(request, response);
      case Decision.Fail fail -> {
        HttpStatus status =
            fail.kind() == FailureKind.SLOW_ERROR
                ? HttpStatus.INTERNAL_SERVER_ERROR
                : HttpStatus.SERVICE_UNAVAILABLE;
        String code = status == HttpStatus.INTERNAL_SERVER_ERROR ? "INTERNAL" : "UNAVAILABLE";
        String message =
            status == HttpStatus.INTERNAL_SERVER_ERROR ? "internal error" : "service unavailable";
        ErrorShapes.write(upstream.get(), status, code, message, response);
      }
    }
  }
}
