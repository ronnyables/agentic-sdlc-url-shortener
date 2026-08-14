package com.schwab.shortener.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateUrlRequest(
        @NotBlank(message = "url must not be blank") String url,
        String customAlias,
        @Positive(message = "ttlSeconds must be positive if provided") Long ttlSeconds
) { }
