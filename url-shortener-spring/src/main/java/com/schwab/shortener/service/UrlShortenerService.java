package com.schwab.shortener.service;

import com.schwab.shortener.exception.*;
import com.schwab.shortener.model.ClickEvent;
import com.schwab.shortener.model.ShortUrl;
import com.schwab.shortener.repository.ClickEventRepository;
import com.schwab.shortener.repository.ShortUrlRepository;
import com.schwab.shortener.util.Base62Encoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Core business logic. Deliberately kept independent of the web layer
 * (no HTTP types imported here) so it can be unit tested with Mockito
 * against mocked repositories, the same separation of concerns as the
 * zero-dependency reference build (see docs/01-architecture.md).
 *
 * Note on the auto-increment sequence: {@code AtomicLong} is process-local,
 * so this is safe for a single Spring Boot instance (this prototype's
 * target) but would collide across multiple instances behind a load
 * balancer. A production rollout would replace it with a DB sequence or a
 * Snowflake-style distributed id generator - see docs/04-testing-and-tradeoffs.md.
 */
@Service
public class UrlShortenerService {

    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,32}$");
    private static final Set<String> RESERVED_ALIASES = Set.of(
            "api", "health", "admin", "static", "favicon.ico", "urls", "metrics", "robots.txt", "actuator", "h2-console");

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;
    private final RateLimiter rateLimiter;
    private final String publicBaseUrl;
    private final String selfHost;

    private final AtomicLong sequence = new AtomicLong(1_000_000L);

    public UrlShortenerService(ShortUrlRepository shortUrlRepository,
                                ClickEventRepository clickEventRepository,
                                RateLimiter rateLimiter,
                                @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.shortUrlRepository = shortUrlRepository;
        this.clickEventRepository = clickEventRepository;
        this.rateLimiter = rateLimiter;
        this.publicBaseUrl = publicBaseUrl;
        this.selfHost = URI.create(publicBaseUrl).getHost();
    }

    @Transactional
    public CreateUrlResult shorten(String longUrl, String customAlias, Long ttlSeconds, String clientKey) {
        if (clientKey != null && !rateLimiter.tryConsume(clientKey)) {
            throw new RateLimitExceededException("Rate limit exceeded for client: " + clientKey);
        }

        String normalized = validateAndNormalizeUrl(longUrl);

        if (customAlias != null && !customAlias.isBlank()) {
            String alias = customAlias.trim();
            validateAlias(alias);
            if (shortUrlRepository.existsById(alias)) {
                throw new AliasConflictException("Alias already in use: " + alias);
            }
            ShortUrl shortUrl = ShortUrl.builder()
                    .code(alias).longUrl(normalized).createdAt(Instant.now())
                    .expiresAt(expiryOf(ttlSeconds)).customAlias(true).active(true).build();
            shortUrlRepository.save(shortUrl);
            return new CreateUrlResult(shortUrl, false);
        }

        if (ttlSeconds == null) {
            var existing = shortUrlRepository.findFirstByLongUrlAndActiveTrue(normalized);
            if (existing.isPresent() && !existing.get().isExpired(Instant.now())) {
                return new CreateUrlResult(existing.get(), true);
            }
        }

        String code;
        do {
            code = Base62Encoder.encode(sequence.getAndIncrement());
        } while (shortUrlRepository.existsById(code));

        ShortUrl shortUrl = ShortUrl.builder()
                .code(code).longUrl(normalized).createdAt(Instant.now())
                .expiresAt(expiryOf(ttlSeconds)).customAlias(false).active(true).build();
        shortUrlRepository.save(shortUrl);
        return new CreateUrlResult(shortUrl, false);
    }

    @Cacheable(value = "shortUrls", key = "#code")
    @Transactional(readOnly = true)
    public ShortUrl findActive(String code) {
        ShortUrl shortUrl = shortUrlRepository.findById(code)
                .orElseThrow(() -> new UrlNotFoundException("No such short URL: " + code));
        if (!shortUrl.isActive()) {
            throw new UrlNotFoundException("Short URL has been deleted: " + code);
        }
        return shortUrl;
    }

    /** Resolves for redirect purposes: same as findActive but also enforces expiry (kept separate so metadata lookups don't 410). */
    public ShortUrl resolve(String code) {
        ShortUrl shortUrl = findActive(code);
        if (shortUrl.isExpired(Instant.now())) {
            throw new UrlExpiredException("Short URL has expired: " + code);
        }
        return shortUrl;
    }

    @Transactional(readOnly = true)
    public ShortUrl getMetadata(String code) {
        return shortUrlRepository.findById(code)
                .orElseThrow(() -> new UrlNotFoundException("No such short URL: " + code));
    }

    @Transactional
    public void recordClick(String code, String referrer, String userAgent, String clientHash) {
        ClickEvent event = ClickEvent.builder()
                .shortCode(code)
                .timestamp(Instant.now())
                .referrer(referrer == null || referrer.isBlank() ? "direct" : referrer)
                .userAgent(userAgent == null || userAgent.isBlank() ? "unknown" : userAgent)
                .clientHash(clientHash)
                .build();
        clickEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public AnalyticsSummary getAnalytics(String code) {
        ShortUrl shortUrl = getMetadata(code);
        List<ClickEvent> events = clickEventRepository.findByShortCodeOrderByTimestampAsc(code);
        Map<String, Long> byReferrer = events.stream()
                .collect(Collectors.groupingBy(ClickEvent::getReferrer, LinkedHashMap::new, Collectors.counting()));
        Instant lastClick = events.isEmpty() ? null : events.get(events.size() - 1).getTimestamp();
        List<String> recent = events.stream()
                .skip(Math.max(0, events.size() - 10))
                .map(e -> e.getTimestamp() + " ref=" + e.getReferrer() + " ua=" + e.getUserAgent())
                .collect(Collectors.toList());
        return new AnalyticsSummary(code, events.size(), shortUrl.getCreatedAt(), lastClick, byReferrer, recent);
    }

    @CacheEvict(value = "shortUrls", key = "#code")
    @Transactional
    public boolean delete(String code) {
        return shortUrlRepository.findById(code).map(shortUrl -> {
            shortUrl.setActive(false);
            shortUrlRepository.save(shortUrl);
            return true;
        }).orElse(false);
    }

    /** Reliability sweep: deactivates expired links. Invoked on a schedule by ExpirySweeper. */
    @Transactional
    public int sweepExpired() {
        List<ShortUrl> expired = shortUrlRepository.findByActiveTrueAndExpiresAtBefore(Instant.now());
        expired.forEach(u -> u.setActive(false));
        shortUrlRepository.saveAll(expired);
        return expired.size();
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
