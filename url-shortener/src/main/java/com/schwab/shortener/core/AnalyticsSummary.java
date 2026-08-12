package com.schwab.shortener.core;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AnalyticsSummary {
    public final String code;
    public final long totalClicks;
    public final Instant createdAt;
    public final Instant lastClickAt; // nullable
    public final Map<String, Long> clicksByReferrer;
    public final List<String> recentEvents; // formatted, most recent last

    public AnalyticsSummary(String code, long totalClicks, Instant createdAt, Instant lastClickAt,
                             Map<String, Long> clicksByReferrer, List<String> recentEvents) {
        this.code = code;
        this.totalClicks = totalClicks;
        this.createdAt = createdAt;
        this.lastClickAt = lastClickAt;
        this.clicksByReferrer = clicksByReferrer;
        this.recentEvents = recentEvents;
    }
}
