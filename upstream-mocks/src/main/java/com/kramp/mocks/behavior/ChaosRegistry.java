package com.kramp.mocks.behavior;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe store of chaos overrides; absent means HEALTHY. */
public class ChaosRegistry {

  private final Map<Upstream, ChaosSetting> settings = new ConcurrentHashMap<>();

  public ChaosSetting get(Upstream upstream) {
    return settings.getOrDefault(upstream, ChaosSetting.HEALTHY);
  }

  public void set(Upstream upstream, ChaosSetting setting) {
    settings.put(upstream, setting);
  }

  public void reset() {
    settings.clear();
  }

  /** Mode of every upstream keyed by its id, sorted for stable output. */
  public Map<String, ChaosSetting> snapshot() {
    Map<String, ChaosSetting> snapshot = new TreeMap<>();
    for (Upstream upstream : Upstream.values()) {
      snapshot.put(upstream.id(), get(upstream));
    }
    return snapshot;
  }
}
