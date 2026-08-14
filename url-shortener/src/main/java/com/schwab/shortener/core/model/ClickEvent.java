package com.schwab.shortener.core.model;

import java.time.Instant;

/** A single click/redirect event captured for analytics. */
public final class ClickEvent {

    private final String shortCode;
    private final Instant timestamp;
    private final String referrer;   // "direct" if absent
    private final String userAgent;  // "unknown" if absent
    private final String clientHash; // hashed client identifier, never raw IP (privacy guardrail)

    public ClickEvent(String shortCode, Instant timestamp, String referrer, String userAgent, String clientHash) {
        this.shortCode = shortCode;
        this.timestamp = timestamp;
        this.referrer = referrer == null || referrer.isBlank() ? "direct" : referrer;
        this.userAgent = userAgent == null || userAgent.isBlank() ? "unknown" : userAgent;
        this.clientHash = clientHash;
    }

    public String getShortCode() { return shortCode; }
    public Instant getTimestamp() { return timestamp; }
    public String getReferrer() { return referrer; }
    public String getUserAgent() { return userAgent; }
    public String getClientHash() { return clientHash; }
}
