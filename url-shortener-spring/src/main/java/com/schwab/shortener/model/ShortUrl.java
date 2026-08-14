package com.schwab.shortener.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A shortened URL mapping. {@code code} is the natural primary key (the
 * short code itself, e.g. "4C92" or a custom alias) - there is no separate
 * surrogate id, since the code IS the business identifier and is already
 * unique by construction (see UrlShortenerService).
 *
 * Written by hand (no Lombok) so this class has zero dependency on
 * annotation-processor configuration being correct on your machine - see
 * docs/06-spring-boot-rebuild.md for why that trade-off was made after an
 * initial Lombok-based version hit exactly that class of build issue.
 */
@Entity
@Table(name = "short_urls", indexes = {
        @Index(name = "idx_short_urls_long_url", columnList = "longUrl")
})
public class ShortUrl {

    @Id
    @Column(length = 64)
    private String code;

    @Column(nullable = false, length = 2048)
    private String longUrl;

    @Column(nullable = false)
    private Instant createdAt;

    /** Nullable = never expires. */
    @Column
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean customAlias;

    @Column(nullable = false)
    private boolean active;

    protected ShortUrl() {
        // required by JPA
    }

    private ShortUrl(Builder b) {
        this.code = b.code;
        this.longUrl = b.longUrl;
        this.createdAt = b.createdAt;
        this.expiresAt = b.expiresAt;
        this.customAlias = b.customAlias;
        this.active = b.active;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLongUrl() { return longUrl; }
    public void setLongUrl(String longUrl) { this.longUrl = longUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isCustomAlias() { return customAlias; }
    public void setCustomAlias(boolean customAlias) { this.customAlias = customAlias; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public static final class Builder {
        private String code;
        private String longUrl;
        private Instant createdAt;
        private Instant expiresAt;
        private boolean customAlias;
        private boolean active;

        public Builder code(String code) { this.code = code; return this; }
        public Builder longUrl(String longUrl) { this.longUrl = longUrl; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder customAlias(boolean customAlias) { this.customAlias = customAlias; return this; }
        public Builder active(boolean active) { this.active = active; return this; }

        public ShortUrl build() {
            return new ShortUrl(this);
        }
    }
}
