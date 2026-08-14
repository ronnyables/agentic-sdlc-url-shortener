package com.schwab.shortener.service;

import com.schwab.shortener.exception.*;
import com.schwab.shortener.model.ClickEvent;
import com.schwab.shortener.model.ShortUrl;
import com.schwab.shortener.repository.ClickEventRepository;
import com.schwab.shortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    @Mock private ShortUrlRepository shortUrlRepository;
    @Mock private ClickEventRepository clickEventRepository;

    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        // Real (non-mocked) rate limiter with a generous bucket so it never interferes with logic tests,
        // except in the one test that specifically targets it.
        RateLimiter permissive = new RateLimiter(1000, 1000);
        service = new UrlShortenerService(shortUrlRepository, clickEventRepository, permissive, "http://short.ly");
        lenient().when(shortUrlRepository.save(any(ShortUrl.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shortenValidUrlSavesAndReturnsNewCode() {
        when(shortUrlRepository.existsById(anyString())).thenReturn(false);

        CreateUrlResult result = service.shorten("https://example.com/page", null, null, "client-1");

        assertThat(result.shortUrl().getCode()).isNotBlank();
        assertThat(result.shortUrl().getLongUrl()).isEqualTo("https://example.com/page");
        verify(shortUrlRepository).save(any(ShortUrl.class));
    }

    @Test
    void rejectsMalformedUrl() {
        assertThatThrownBy(() -> service.shorten("not a url", null, null, "c"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThatThrownBy(() -> service.shorten("ftp://example.com/file", null, null, "c"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsSelfReferencingUrl() {
        assertThatThrownBy(() -> service.shorten("http://short.ly/abc", null, null, "c"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void customAliasIsHonoredWhenAvailable() {
        when(shortUrlRepository.existsById("MyLink")).thenReturn(false);

        CreateUrlResult result = service.shorten("https://example.com/a", "MyLink", null, "c");

        assertThat(result.shortUrl().getCode()).isEqualTo("MyLink");
        assertThat(result.shortUrl().isCustomAlias()).isTrue();
    }

    @Test
    void duplicateCustomAliasThrowsConflict() {
        when(shortUrlRepository.existsById("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.shorten("https://example.com/b", "taken", null, "c"))
                .isInstanceOf(AliasConflictException.class);
    }

    @Test
    void invalidAliasFormatIsRejected() {
        assertThatThrownBy(() -> service.shorten("https://example.com/a", "x", null, "c"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void reservedAliasIsRejected() {
        assertThatThrownBy(() -> service.shorten("https://example.com/a", "admin", null, "c"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void duplicateLongUrlWithoutTtlIsDeduplicated() {
        ShortUrl existing = ShortUrl.builder().code("EXIST1").longUrl("https://example.com/dup")
                .createdAt(Instant.now()).active(true).customAlias(false).build();
        when(shortUrlRepository.findFirstByLongUrlAndActiveTrue("https://example.com/dup"))
                .thenReturn(Optional.of(existing));

        CreateUrlResult result = service.shorten("https://example.com/dup", null, null, "c");

        assertThat(result.deduped()).isTrue();
        assertThat(result.shortUrl().getCode()).isEqualTo("EXIST1");
        verify(shortUrlRepository, never()).save(any());
    }

    @Test
    void resolveThrowsNotFoundForUnknownCode() {
        when(shortUrlRepository.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolve("nope")).isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void resolveThrowsExpiredForPastTtl() {
        ShortUrl expired = ShortUrl.builder().code("OLD1").longUrl("https://example.com/x")
                .createdAt(Instant.now().minusSeconds(120)).expiresAt(Instant.now().minusSeconds(1))
                .active(true).customAlias(false).build();
        when(shortUrlRepository.findById("OLD1")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.resolve("OLD1")).isInstanceOf(UrlExpiredException.class);
    }

    @Test
    void deleteDeactivatesExistingCode() {
        ShortUrl url = ShortUrl.builder().code("DEL1").longUrl("https://example.com/d")
                .createdAt(Instant.now()).active(true).customAlias(false).build();
        when(shortUrlRepository.findById("DEL1")).thenReturn(Optional.of(url));

        boolean deleted = service.delete("DEL1");

        assertThat(deleted).isTrue();
        ArgumentCaptor<ShortUrl> captor = ArgumentCaptor.forClass(ShortUrl.class);
        verify(shortUrlRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void deleteOfUnknownCodeReturnsFalse() {
        when(shortUrlRepository.findById("nope")).thenReturn(Optional.empty());
        assertThat(service.delete("nope")).isFalse();
    }

    @Test
    void analyticsAggregatesClicksByReferrer() {
        ShortUrl url = ShortUrl.builder().code("AN1").longUrl("https://example.com/an")
                .createdAt(Instant.now()).active(true).customAlias(false).build();
        when(shortUrlRepository.findById("AN1")).thenReturn(Optional.of(url));
        when(clickEventRepository.findByShortCodeOrderByTimestampAsc("AN1")).thenReturn(List.of(
                ClickEvent.builder().shortCode("AN1").timestamp(Instant.now()).referrer("https://twitter.com").userAgent("curl").build(),
                ClickEvent.builder().shortCode("AN1").timestamp(Instant.now()).referrer("https://twitter.com").userAgent("curl").build(),
                ClickEvent.builder().shortCode("AN1").timestamp(Instant.now()).referrer("direct").userAgent("curl").build()
        ));

        AnalyticsSummary summary = service.getAnalytics("AN1");

        assertThat(summary.totalClicks()).isEqualTo(3);
        assertThat(summary.clicksByReferrer().get("https://twitter.com")).isEqualTo(2L);
        assertThat(summary.clicksByReferrer().get("direct")).isEqualTo(1L);
    }

    @Test
    void rateLimitExceededThrowsBeforeAnyValidation() {
        UrlShortenerService limited = new UrlShortenerService(
                shortUrlRepository, clickEventRepository, new RateLimiter(1, 0.0001), "http://short.ly");
        when(shortUrlRepository.existsById(anyString())).thenReturn(false);

        limited.shorten("https://example.com/1", null, null, "same-client");

        assertThatThrownBy(() -> limited.shorten("https://example.com/2", null, null, "same-client"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void sweepExpiredDeactivatesOnlyExpiredLinks() {
        ShortUrl expiring = ShortUrl.builder().code("SWEEP1").longUrl("https://example.com/soon")
                .createdAt(Instant.now()).expiresAt(Instant.now().minusSeconds(1)).active(true).customAlias(false).build();
        when(shortUrlRepository.findByActiveTrueAndExpiresAtBefore(any())).thenReturn(List.of(expiring));

        int swept = service.sweepExpired();

        assertThat(swept).isEqualTo(1);
        assertThat(expiring.isActive()).isFalse();
        verify(shortUrlRepository).saveAll(List.of(expiring));
    }
}
