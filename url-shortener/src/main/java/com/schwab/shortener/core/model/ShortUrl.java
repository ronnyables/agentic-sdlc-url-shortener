package com.schwab.shortener.core.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Immutable-ish domain record for a shortened URL mapping.
 * Mutable fields (active) are guarded so the object can be safely shared
 * across the in-memory repository without external synchronization.
 */
public final class ShortUrl {

    private final String code;
    private final String longUrl;
    private final Instant createdAt;
    private final Instant expiresAt; // nullable = never expires
    private final boolean customAlias;
    private final AtomicBoolean active = new AtomicBoolean(true);

    public ShortUrl(String code, String longUrl, Instant createdAt, Instant expiresAt, boolean customAlias) {
        this.code = code;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.customAlias = customAlias;
    }

    public String getCode() { return code; }
    public String getLongUrl() { return longUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isCustomAlias() { return customAlias; }
    public boolean isActive() { return active.get(); }
    public void deactivate() { active.set(false); }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
