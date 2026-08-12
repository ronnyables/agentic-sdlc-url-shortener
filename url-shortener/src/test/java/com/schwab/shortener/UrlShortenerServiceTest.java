package com.schwab.shortener;

import com.schwab.shortener.core.*;
import com.schwab.shortener.core.exceptions.*;
import com.schwab.shortener.core.model.ShortUrl;
import static com.schwab.shortener.testkit.MiniTest.*;

public class UrlShortenerServiceTest {

    private UrlShortenerService newService() {
        return new UrlShortenerService(
                new InMemoryUrlRepository(),
                new InMemoryClickEventStore(),
                new RateLimiter(1000, 1000), // effectively unlimited for logic tests
                new LruCache<>(1000),
                "short.ly");
    }

    public void testShortenValidUrlProducesResolvableCode() {
        UrlShortenerService service = newService();
        CreateUrlResult result = service.shorten("https://example.com/page", null, null, "client-1");
        assertNotNull(result.shortUrl.getCode(), "generated code");
        ShortUrl resolved = service.resolve(result.shortUrl.getCode());
        assertEquals("https://example.com/page", resolved.getLongUrl(), "resolved long URL");
    }

    public void testRejectsMalformedUrl() {
        UrlShortenerService service = newService();
        assertThrows(InvalidUrlException.class, () -> service.shorten("not a url", null, null, "c"), "malformed URL");
    }

    public void testRejectsNonHttpScheme() {
        UrlShortenerService service = newService();
        assertThrows(InvalidUrlException.class, () -> service.shorten("ftp://example.com/file", null, null, "c"), "ftp scheme rejected");
    }

    public void testRejectsSelfReferencingUrlToPreventRedirectLoop() {
        UrlShortenerService service = newService();
        assertThrows(InvalidUrlException.class, () -> service.shorten("https://short.ly/abc", null, null, "c"), "self-referencing URL");
    }

    public void testCustomAliasIsHonoredAndCaseSensitive() {
        UrlShortenerService service = newService();
        CreateUrlResult result = service.shorten("https://example.com/a", "MyLink", null, "c");
        assertEquals("MyLink", result.shortUrl.getCode(), "custom alias preserved as-is");
    }

    public void testDuplicateCustomAliasIsRejected() {
        UrlShortenerService service = newService();
        service.shorten("https://example.com/a", "taken", null, "c");
        assertThrows(AliasConflictException.class,
                () -> service.shorten("https://example.com/b", "taken", null, "c"), "alias reuse");
    }

    public void testInvalidAliasFormatIsRejected() {
        UrlShortenerService service = newService();
        assertThrows(InvalidUrlException.class, () -> service.shorten("https://example.com/a", "x", null, "c"), "alias too short");
        assertThrows(InvalidUrlException.class, () -> service.shorten("https://example.com/a", "has space", null, "c"), "alias with space");
    }

    public void testReservedAliasIsRejected() {
        UrlShortenerService service = newService();
        assertThrows(InvalidUrlException.class, () -> service.shorten("https://example.com/a", "admin", null, "c"), "reserved alias");
    }

    public void testDuplicateLongUrlWithoutTtlIsDeduplicated() {
        UrlShortenerService service = newService();
        CreateUrlResult first = service.shorten("https://example.com/dup", null, null, "c");
        CreateUrlResult second = service.shorten("https://example.com/dup", null, null, "c");
        assertEquals(first.shortUrl.getCode(), second.shortUrl.getCode(), "same long URL should reuse the same code");
        assertFalse(first.deduped, "first insert is not a dedup hit");
        assertTrue(second.deduped, "second insert should be flagged as deduplicated");
    }

    public void testTtlExpiryPreventsResolution() throws InterruptedException {
        UrlShortenerService service = newService();
        CreateUrlResult result = service.shorten("https://example.com/ttl", null, 0L, "c");
        Thread.sleep(5);
        assertThrows(UrlExpiredException.class, () -> service.resolve(result.shortUrl.getCode()), "expired link should not resolve");
    }

    public void testDeleteDeactivatesAndBlocksResolution() {
        UrlShortenerService service = newService();
        CreateUrlResult result = service.shorten("https://example.com/del", null, null, "c");
        assertTrue(service.delete(result.shortUrl.getCode()), "delete should succeed for an existing code");
        assertThrows(UrlNotFoundException.class, () -> service.resolve(result.shortUrl.getCode()), "deleted link should not resolve");
    }

    public void testDeleteOfUnknownCodeReturnsFalse() {
        UrlShortenerService service = newService();
        assertFalse(service.delete("nope"), "deleting a non-existent code returns false, not an exception");
    }

    public void testResolveOfUnknownCodeThrowsNotFound() {
        UrlShortenerService service = newService();
        assertThrows(UrlNotFoundException.class, () -> service.resolve("nope"), "unknown code");
    }

    public void testAnalyticsAggregatesClicksByReferrer() {
        UrlShortenerService service = newService();
        CreateUrlResult result = service.shorten("https://example.com/an", null, null, "c");
        String code = result.shortUrl.getCode();
        service.recordClick(code, "https://twitter.com", "curl/8", "hash1");
        service.recordClick(code, "https://twitter.com", "curl/8", "hash2");
        service.recordClick(code, null, "curl/8", "hash3");
        AnalyticsSummary summary = service.getAnalytics(code);
        assertEquals(3L, summary.totalClicks, "total clicks");
        assertEquals(2L, summary.clicksByReferrer.get("https://twitter.com"), "twitter referrer count");
        assertEquals(1L, summary.clicksByReferrer.get("direct"), "direct referrer count (null normalized)");
    }

    public void testRateLimitExceededThrowsBeforeAnyValidation() {
        UrlShortenerService service = new UrlShortenerService(
                new InMemoryUrlRepository(), new InMemoryClickEventStore(),
                new RateLimiter(1, 0.0001), new LruCache<>(10), "short.ly");
        service.shorten("https://example.com/1", null, null, "same-client");
        assertThrows(RateLimitExceededException.class,
                () -> service.shorten("https://example.com/2", null, null, "same-client"), "second request from same client exceeds burst of 1");
    }

    public void testSweepExpiredDeactivatesOnlyExpiredLinks() throws InterruptedException {
        UrlShortenerService service = newService();
        CreateUrlResult expiring = service.shorten("https://example.com/soon", null, 0L, "c");
        CreateUrlResult persistent = service.shorten("https://example.com/forever", null, null, "c");
        Thread.sleep(5);
        int swept = service.sweepExpired();
        assertEquals(1, swept, "exactly one link should have been swept");
        assertThrows(UrlNotFoundException.class, () -> service.resolve(expiring.shortUrl.getCode()), "swept link is deactivated");
        assertNoThrow(() -> service.resolve(persistent.shortUrl.getCode()), "unrelated link is unaffected by the sweep");
    }
}
