package com.schwab.shortener;

import com.schwab.shortener.core.RateLimiter;
import static com.schwab.shortener.testkit.MiniTest.*;

public class RateLimiterTest {

    public void testAllowsUpToCapacityThenBlocks() {
        RateLimiter limiter = new RateLimiter(5, 0.0001); // effectively no refill during the test
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryConsume("client-a"), "request " + i + " should be within burst capacity");
        }
        assertFalse(limiter.tryConsume("client-a"), "6th immediate request should be rate limited");
    }

    public void testKeysAreIndependent() {
        RateLimiter limiter = new RateLimiter(1, 0.0001);
        assertTrue(limiter.tryConsume("client-a"), "client-a first request");
        assertFalse(limiter.tryConsume("client-a"), "client-a second request should be blocked");
        assertTrue(limiter.tryConsume("client-b"), "client-b should have its own independent bucket");
    }

    public void testRefillsOverTime() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(1, 20); // 20 tokens/sec -> refills in 50ms
        assertTrue(limiter.tryConsume("client-c"), "first token available immediately");
        assertFalse(limiter.tryConsume("client-c"), "bucket should be empty right after consuming");
        Thread.sleep(120);
        assertTrue(limiter.tryConsume("client-c"), "bucket should have refilled after waiting");
    }
}
