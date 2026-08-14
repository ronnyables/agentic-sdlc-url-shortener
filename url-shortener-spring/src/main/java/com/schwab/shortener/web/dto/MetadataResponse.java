package com.schwab.shortener.web.dto;

import java.time.Instant;

public record MetadataResponse(
        String shortCode,
        String shortUrl,
        String longUrl,
        Instant createdAt,
        Instant expiresAt,
        boolean active,
        boolean customAlias
) { }
