package com.schwab.shortener.core;

import com.schwab.shortener.core.exceptions.*;
import com.schwab.shortener.core.model.ClickEvent;
import com.schwab.shortener.core.model.ShortUrl;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Core business logic for the URL shortener. Deliberately framework-agnostic
 * (no HTTP/servlet/JPA types) so it can be unit tested in isolation and
 * reused behind any transport (see web/ package for the HTTP adapter).
 */
public final class UrlShortenerService {

    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,32}$");
    private static final java.util.Set<String> RESERVED_ALIASES = java.util.Set.of(
            "api", "health", "admin", "static", "favicon.ico", "urls", "metrics", "robots.txt");

    private final UrlRepository repository;
    private final ClickEventStore clickEventStore;
    private final RateLimiter rateLimiter;
    private final LruCache<String, ShortUrl> cache;
    private final String selfHost; // used to reject shortening our own short links (loop prevention)

    // sequence used for auto-generated codes; starts high enough to yield short, non-trivial codes
    private final AtomicLong sequence = new AtomicLong(1_000_000L);

    public UrlShortenerService(UrlRepository repository, ClickEventStore clickEventStore,
                                RateLimiter rateLimiter, LruCache<String, ShortUrl> cache, String selfHost) {
        this.repository = repository;
        this.clickEventStore = clickEventStore;
        this.rateLimiter = rateLimiter;
        this.cache = cache;
        this.selfHost = selfHost;
    }

    public CreateUrlResult shorten(String longUrl, String customAlias, Long ttlSeconds, String clientKey) {
        if (rateLimiter != null && clientKey != null && !rateLimiter.tryConsume(clientKey)) {
            throw new RateLimitExceededException("Rate limit exceeded for client: " + clientKey);
        }

        String normalized = validateAndNormalizeUrl(longUrl);

        if (customAlias != null && !customAlias.isBlank()) {
            String alias = customAlias.trim();
            validateAlias(alias);
            if (repository.existsByCode(alias)) {
                throw new AliasConflictException("Alias already in use: " + alias);
            }
            ShortUrl shortUrl = new ShortUrl(alias, normalized, Instant.now(), expiryOf(ttlSeconds), true);
            repository.save(shortUrl);
            cache.put(alias, shortUrl);
            return new CreateUrlResult(shortUrl, false);
        }

        // De-duplicate: reuse an existing active mapping for the same long URL when no
        // custom alias/TTL override is requested, to keep the mapping table compact.
        if (ttlSeconds == null) {
            var existing = repository.findExistingCodeForLongUrl(normalized);
            if (existing.isPresent()) {
                ShortUrl existingUrl = repository.findByCode(existing.get()).orElse(null);
                if (existingUrl != null && existingUrl.isActive() && !existingUrl.isExpired(Instant.now())) {
                    return new CreateUrlResult(existingUrl, true);
                }
            }
        }

        String code;
        do {
            code = Base62Encoder.encode(sequence.getAndIncrement());
        } while (repository.existsByCode(code)); // defensive; collisions are not expected with a monotonic sequence

        ShortUrl shortUrl = new ShortUrl(code, normalized, Instant.now(), expiryOf(ttlSeconds), false);
        repository.save(shortUrl);
        cache.put(code, shortUrl);
        return new CreateUrlResult(shortUrl, false);
    }

    public ShortUrl resolve(String code) {
        ShortUrl cached = cache.get(code);
        ShortUrl shortUrl = cached != null ? cached : repository.findByCode(code)
                .orElseThrow(() -> new UrlNotFoundException("No such short URL: " + code));
        if (cached == null) {
            cache.put(code, shortUrl);
        }
        if (!shortUrl.isActive()) {
            throw new UrlNotFoundException("Short URL has been deleted: " + code);
        }
        if (shortUrl.isExpired(Instant.now())) {
            throw new UrlExpiredException("Short URL has expired: " + code);
        }
        return shortUrl;
    }

    public ShortUrl getMetadata(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new UrlNotFoundException("No such short URL: " + code));
    }

    public void recordClick(String code, String referrer, String userAgent, String clientHash) {
        clickEventStore.record(new ClickEvent(code, Instant.now(), referrer, userAgent, clientHash));
    }

    public AnalyticsSummary getAnalytics(String code) {
        ShortUrl shortUrl = getMetadata(code);
        List<ClickEvent> events = clickEventStore.findByCode(code);
        Map<String, Long> byReferrer = events.stream()
                .collect(Collectors.groupingBy(ClickEvent::getReferrer, LinkedHashMap::new, Collectors.counting()));
        Instant lastClick = events.isEmpty() ? null : events.get(events.size() - 1).getTimestamp();
        List<String> recent = events.stream()
                .skip(Math.max(0, events.size() - 10))
                .map(e -> e.getTimestamp() + " ref=" + e.getReferrer() + " ua=" + e.getUserAgent())
                .collect(Collectors.toList());
        return new AnalyticsSummary(code, events.size(), shortUrl.getCreatedAt(), lastClick, byReferrer, recent);
    }

    public boolean delete(String code) {
        ShortUrl shortUrl = repository.findByCode(code).orElse(null);
        if (shortUrl == null) {
            return false;
        }
        shortUrl.deactivate();
        cache.invalidate(code);
        return true;
    }

    /** Reliability sweep: proactively evicts expired entries from the cache (repo keeps history for audit). */
    public int sweepExpired() {
        int count = 0;
        Instant now = Instant.now();
        for (ShortUrl url : repository.all()) {
            if (url.isExpired(now) && url.isActive()) {
                url.deactivate();
                cache.invalidate(url.getCode());
                count++;
            }
        }
        return count;
    }

    private Instant expiryOf(Long ttlSeconds) {
        return ttlSeconds == null ? null : Instant.now().plusSeconds(ttlSeconds);
    }

    private void validateAlias(String alias) {
        if (!ALIAS_PATTERN.matcher(alias).matches()) {
            throw new InvalidUrlException("Alias must be 3-32 chars of letters/digits/-/_: " + alias);
        }
        if (RESERVED_ALIASES.contains(alias.toLowerCase())) {
            throw new InvalidUrlException("Alias is reserved: " + alias);
        }
    }

    private String validateAndNormalizeUrl(String longUrl) {
        if (longUrl == null || longUrl.isBlank()) {
            throw new InvalidUrlException("URL must not be empty");
        }
        String trimmed = longUrl.trim();
        if (trimmed.length() > 2048) {
            throw new InvalidUrlException("URL exceeds maximum length of 2048 characters");
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("Malformed URL: " + trimmed);
        }
        if (uri.getScheme() == null || !(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))) {
            throw new InvalidUrlException("URL must use http or https scheme: " + trimmed);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("URL must include a host: " + trimmed);
        }
        if (selfHost != null && uri.getHost().equalsIgnoreCase(selfHost)) {
            throw new InvalidUrlException("Refusing to shorten a URL that points back to this service (loop prevention)");
        }
        return trimmed;
    }
}
