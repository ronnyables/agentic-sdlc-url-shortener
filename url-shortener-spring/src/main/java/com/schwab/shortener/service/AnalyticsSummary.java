package com.schwab.shortener.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AnalyticsSummary(
        String code,
        long totalClicks,
        Instant createdAt,
        Instant lastClickAt,
        Map<String, Long> clicksByReferrer,
        List<String> recentEvents
) { }
