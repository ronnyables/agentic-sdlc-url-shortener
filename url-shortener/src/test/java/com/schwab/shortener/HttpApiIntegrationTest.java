package com.schwab.shortener;

import com.schwab.shortener.core.*;
import com.schwab.shortener.web.HttpServerApp;
import static com.schwab.shortener.testkit.MiniTest.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Full-stack integration test: boots a real HttpServerApp on an ephemeral
 * port and drives it with java.net.http.HttpClient, exercising the same
 * code path a real client would use.
 */
public class HttpApiIntegrationTest {

    private HttpServerApp start() throws Exception {
        UrlShortenerService service = new UrlShortenerService(
                new InMemoryUrlRepository(), new InMemoryClickEventStore(),
                new RateLimiter(1000, 1000), new LruCache<>(1000), "localhost");
        HttpServerApp app = new HttpServerApp(0, service, "http://localhost");
        app.start();
        return app;
    }

    public void testCreateRedirectAnalyticsRoundTrip() throws Exception {
        HttpServerApp app = start();
        try {
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(2)).build();
            String base = "http://localhost:" + app.getPort();

            HttpResponse<String> create = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/urls"))
                            .POST(HttpRequest.BodyPublishers.ofString("{\"url\":\"https://example.com/integration\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(201, create.statusCode(), "create should return 201");
            assertTrue(create.body().contains("\"shortCode\""), "create response should include shortCode");

            String code = extractField(create.body(), "shortCode");

            HttpResponse<String> redirect = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/" + code)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(302, redirect.statusCode(), "redirect should return 302");
            assertEquals("https://example.com/integration", redirect.headers().firstValue("Location").orElse(null), "Location header");

            HttpResponse<String> analytics = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/urls/" + code + "/analytics")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, analytics.statusCode(), "analytics should return 200");
            assertTrue(analytics.body().contains("\"totalClicks\":1"), "one click should have been recorded by the redirect above");
        } finally {
            app.stop(0);
        }
    }

    public void testHealthEndpointReportsUp() throws Exception {
        HttpServerApp app = start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> health = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + app.getPort() + "/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode(), "health check status");
            assertTrue(health.body().contains("\"UP\""), "health body");
        } finally {
            app.stop(0);
        }
    }

    public void testUnknownCodeReturns404() throws Exception {
        HttpServerApp app = start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + app.getPort() + "/doesnotexist")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode(), "unknown short code should 404");
        } finally {
            app.stop(0);
        }
    }

    private static String extractField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
