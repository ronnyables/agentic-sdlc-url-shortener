package com.schwab.shortener.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AnalyticsResponse(
        String shortCode,
        long totalClicks,
        Instant createdAt,
        Instant lastClickAt,
        Map<String, Long> clicksByReferrer,
        List<String> recentEvents
) { }
