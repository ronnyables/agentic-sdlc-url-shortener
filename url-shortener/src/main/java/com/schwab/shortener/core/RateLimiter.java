package com.schwab.shortener.core;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple per-key token bucket rate limiter (reliability feature).
 * Not distributed - adequate for a single-instance prototype. A production
 * rollout would back this with Redis (see docs trade-offs).
 */
public final class RateLimiter {

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;
    }

    private final double capacity;
    private final double refillPerSecond;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(double capacity, double refillPerSecond) {
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
