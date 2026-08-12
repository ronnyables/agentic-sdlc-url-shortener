package com.schwab.shortener;

import com.schwab.shortener.core.*;
import com.schwab.shortener.web.HttpServerApp;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Main {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("server.port", System.getenv().getOrDefault("PORT", "8080")));
        String publicBaseUrl = System.getProperty("server.publicBaseUrl", "http://localhost:" + port);
        String selfHost = java.net.URI.create(publicBaseUrl).getHost();

        UrlRepository repository = new InMemoryUrlRepository();
        ClickEventStore clickEventStore = new InMemoryClickEventStore();
        RateLimiter rateLimiter = new RateLimiter(20, 5); // 20 burst, 5 tokens/sec refill per client
        LruCache<String, com.schwab.shortener.core.model.ShortUrl> cache = new LruCache<>(10_000);

        UrlShortenerService service = new UrlShortenerService(repository, clickEventStore, rateLimiter, cache, selfHost);

        // Reliability: background sweeper for expired links (runs every 30s)
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "expiry-sweeper");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            int swept = service.sweepExpired();
            if (swept > 0) {
                System.out.println("[expiry-sweeper] deactivated " + swept + " expired short URL(s)");
            }
        }, 30, 30, TimeUnit.SECONDS);

        HttpServerApp app = new HttpServerApp(port, service, publicBaseUrl);
        app.start();
        System.out.println("URL Shortener listening on " + publicBaseUrl);
        System.out.println("  POST   /api/urls                  create a short URL");
        System.out.println("  GET    /api/urls/{code}            metadata");
        System.out.println("  GET    /api/urls/{code}/analytics  click analytics");
        System.out.println("  DELETE /api/urls/{code}            delete/deactivate");
        System.out.println("  GET    /{code}                     302 redirect");
        System.out.println("  GET    /health                     liveness check");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down URL Shortener...");
            scheduler.shutdownNow();
            app.stop(1);
        }));
    }
}
