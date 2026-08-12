package com.schwab.shortener.web;

import com.schwab.shortener.core.UrlShortenerService;
import com.schwab.shortener.web.handlers.HealthHandler;
import com.schwab.shortener.web.handlers.RedirectHandler;
import com.schwab.shortener.web.handlers.UrlsApiHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HttpServerApp {

    private final HttpServer server;
    private final ExecutorService executor;

    public HttpServerApp(int port, UrlShortenerService service, String publicBaseUrl) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", new HealthHandler());
        server.createContext("/api/urls", new UrlsApiHandler(service, publicBaseUrl));
        server.createContext("/", new RedirectHandler(service)); // longest-prefix match yields correct routing
        // Daemon threads: request-handling threads must never keep the JVM alive on their own
        // (matters both for graceful CLI shutdown and for short-lived test-harness server instances).
        this.executor = Executors.newFixedThreadPool(16, r -> {
            Thread t = new Thread(r, "http-worker");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public void stop(int delaySeconds) {
        server.stop(delaySeconds);
        executor.shutdownNow();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }
}
