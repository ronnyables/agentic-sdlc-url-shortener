package com.schwab.shortener.web.handlers;

import com.schwab.shortener.core.UrlShortenerService;
import com.schwab.shortener.core.exceptions.UrlExpiredException;
import com.schwab.shortener.core.exceptions.UrlNotFoundException;
import com.schwab.shortener.core.model.ShortUrl;
import com.schwab.shortener.web.HttpUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/** Handles GET /{code} -> 302 redirect to the original long URL, recording a click event. */
public final class RedirectHandler implements HttpHandler {

    private final UrlShortenerService service;

    public RedirectHandler(UrlShortenerService service) {
        this.service = service;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendNoBody(exchange, 405);
            return;
        }
        String[] segments = HttpUtil.pathSegments(exchange);
        if (segments.length != 1) {
            HttpUtil.sendError(exchange, 404, "NOT_FOUND", "No such route: " + exchange.getRequestURI());
            return;
        }
        String code = segments[0];
        try {
            ShortUrl shortUrl = service.resolve(code);
            service.recordClick(code,
                    HttpUtil.header(exchange, "Referer", null),
                    HttpUtil.header(exchange, "User-Agent", null),
                    HttpUtil.clientHash(exchange));
            exchange.getResponseHeaders().set("Location", shortUrl.getLongUrl());
            HttpUtil.sendNoBody(exchange, 302);
        } catch (UrlNotFoundException e) {
            HttpUtil.sendError(exchange, 404, "NOT_FOUND", e.getMessage());
        } catch (UrlExpiredException e) {
            HttpUtil.sendError(exchange, 410, "EXPIRED", e.getMessage());
        }
    }
}
