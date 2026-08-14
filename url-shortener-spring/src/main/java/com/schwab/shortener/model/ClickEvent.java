package com.schwab.shortener.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A single click/redirect event, recorded for analytics.
 *
 * Written by hand (no Lombok) - see ShortUrl.java's javadoc for why.
 */
@Entity
@Table(name = "click_events", indexes = {
        @Index(name = "idx_click_events_short_code", columnList = "shortCode")
})
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String shortCode;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(length = 512)
    private String referrer; // "direct" if none was supplied

    @Column(length = 512)
    private String userAgent; // "unknown" if none was supplied

    /** Truncated SHA-256 of the client address - never the raw IP (privacy guardrail, same as the reference build). */
    @Column(length = 16)
    private String clientHash;

    protected ClickEvent() {
        // required by JPA
    }

    private ClickEvent(Builder b) {
        this.id = b.id;
        this.shortCode = b.shortCode;
        this.timestamp = b.timestamp;
        this.referrer = b.referrer;
        this.userAgent = b.userAgent;
        this.clientHash = b.clientHash;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getReferrer() { return referrer; }
    public void setReferrer(String referrer) { this.referrer = referrer; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getClientHash() { return clientHash; }
    public void setClientHash(String clientHash) { this.clientHash = clientHash; }

    public static final class Builder {
        private Long id;
        private String shortCode;
        private Instant timestamp;
        private String referrer;
        private String userAgent;
        private String clientHash;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder shortCode(String shortCode) { this.shortCode = shortCode; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder referrer(String referrer) { this.referrer = referrer; return this; }
        public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public Builder clientHash(String clientHash) { this.clientHash = clientHash; return this; }

        public ClickEvent build() {
            return new ClickEvent(this);
        }
    }
}
