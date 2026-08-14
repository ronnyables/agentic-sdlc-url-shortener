package com.schwab.shortener.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client token-bucket rate limiter. Single-instance/in-memory - adequate
 * for one Spring Boot instance; a multi-instance deployment would back this
 * with Redis instead (see docs/04-testing-and-tradeoffs.md).
 */
@Component
public class RateLimiter {

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;
    }

    private final double capacity;
    private final double refillPerSecond;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(@Value("${app.rate-limit.capacity:20}") double capacity,
                        @Value("${app.rate-limit.refill-per-second:5}") double refillPerSecond) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
    }

    public boolean tryConsume(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> {
            Bucket b = new Bucket();
            b.tokens = capacity;
            b.lastRefillNanos = System.nanoTime();
            return b;
        });
        synchronized (bucket) {
            long now = System.nanoTime();
            double elapsedSeconds = (now - bucket.lastRefillNanos) / 1_000_000_000.0;
            bucket.tokens = Math.min(capacity, bucket.tokens + elapsedSeconds * refillPerSecond);
            bucket.lastRefillNanos = now;
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
