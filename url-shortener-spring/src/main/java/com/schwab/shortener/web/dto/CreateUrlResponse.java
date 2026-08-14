package com.schwab.shortener.web.dto;

import java.time.Instant;

public record CreateUrlResponse(
        String shortCode,
        String shortUrl,
        String longUrl,
        Instant createdAt,
        Instant expiresAt,
        boolean customAlias,
        boolean deduplicated
) { }
