package com.kramp.mocks.chaos;

import com.kramp.mocks.behavior.ChaosMode;
import com.kramp.mocks.behavior.ChaosRegistry;
import com.kramp.mocks.behavior.ChaosSetting;
import com.kramp.mocks.behavior.Upstream;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Demo-time failure injection: switch any upstream into a degraded mode and back. */
@RestController
@RequestMapping("/chaos")
class ChaosController {

  record ChaosRequest(ChaosMode mode, Double slowFactor, Double reliability) {}

  private final ChaosRegistry registry;

  ChaosController(ChaosRegistry registry) {
    this.registry = registry;
  }

  @GetMapping
  Map<String, ChaosSetting> state() {
    return registry.snapshot();
  }

  @PutMapping("/{upstream}")
  ResponseEntity<ChaosSetting> set(
      @PathVariable String upstream, @RequestBody ChaosRequest request) {
    Optional<Upstream> target = Upstream.fromId(upstream);
    if (target.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    ChaosSetting setting =
        ChaosSetting.of(request.mode(), request.slowFactor(), request.reliability());
    registry.set(target.get(), setting);
    return ResponseEntity.ok(setting);
  }

  @DeleteMapping
  ResponseEntity<Void> reset() {
    registry.reset();
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String, String>> invalid(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
  }
}
