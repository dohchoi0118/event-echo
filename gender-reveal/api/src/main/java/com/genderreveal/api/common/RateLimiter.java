package com.genderreveal.api.common;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter keyed by an arbitrary string (typically a guest cookie
 * value). Single-instance only — no shared cache — which fits this app's current scale (one API
 * process). Per-key locking, not a class-wide lock, so unrelated keys don't block each other.
 */
@Component
public class RateLimiter {

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Returns true and records a hit if under the limit; returns false (no hit recorded) otherwise. */
    public boolean allow(String key, int maxHits, Duration window) {
        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(window);
        Deque<Instant> timestamps = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxHits) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
