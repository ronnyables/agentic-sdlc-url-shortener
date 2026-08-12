package com.schwab.shortener.web.handlers;

import com.schwab.shortener.core.AnalyticsSummary;
import com.schwab.shortener.core.CreateUrlResult;
import com.schwab.shortener.core.UrlShortenerService;
import com.schwab.shortener.core.exceptions.*;
import com.schwab.shortener.core.model.ShortUrl;
import com.schwab.shortener.web.HttpUtil;
import com.schwab.shortener.web.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Handles everything under /api/urls: create, metadata, analytics, delete. */
public final class UrlsApiHandler implements HttpHandler {

    private final UrlShortenerService service;
    private final String publicBaseUrl;

    public UrlsApiHandler(UrlShortenerService service, String publicBaseUrl) {
        this.service = service;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String[] segments = HttpUtil.pathSegments(exchange); // e.g. ["api","urls","abc123","analytics"]
            String method = exchange.getRequestMethod();

            if (segments.length == 2 && "POST".equalsIgnoreCase(method)) {
                handleCreate(exchange);
            } else if (segments.length == 3 && "GET".equalsIgnoreCase(method)) {
                handleMetadata(exchange, segments[2]);
            } else if (segments.length == 4 && "GET".equalsIgnoreCase(method) && "analytics".equals(segments[3])) {
                handleAnalytics(exchange, segments[2]);
            } else if (segments.length == 3 && "DELETE".equalsIgnoreCase(method)) {
                handleDelete(exchange, segments[2]);
            } else {
                HttpUtil.sendError(exchange, 404, "NOT_FOUND", "No such route: " + method + " " + exchange.getRequestURI());
            }
        } catch (InvalidUrlException e) {
            HttpUtil.sendError(exchange, 400, "INVALID_URL", e.getMessage());
        } catch (AliasConflictException e) {
            HttpUtil.sendError(exchange, 409, "ALIAS_CONFLICT", e.getMessage());
        } catch (RateLimitExceededException e) {
            HttpUtil.sendError(exchange, 429, "RATE_LIMITED", e.getMessage());
        } catch (UrlNotFoundException e) {
            HttpUtil.sendError(exchange, 404, "NOT_FOUND", e.getMessage());
        } catch (UrlExpiredException e) {
            HttpUtil.sendError(exchange, 410, "EXPIRED", e.getMessage());
        } catch (IllegalArgumentException e) {
            HttpUtil.sendError(exchange, 400, "BAD_REQUEST", e.getMessage());
        } catch (Exception e) {
            HttpUtil.sendError(exchange, 500, "INTERNAL_ERROR", "Unexpected error: " + e.getMessage());
        }
    }

    private void handleCreate(HttpExchange exchange) throws IOException {
        String rawBody = HttpUtil.readBody(exchange);
        Map<String, Object> body = JsonUtil.parseObject(rawBody);
        String url = String.valueOf(HttpUtil.requireString(body, "url"));
        String alias = body.get("customAlias") == null ? null : String.valueOf(body.get("customAlias"));
        Long ttl = body.get("ttlSeconds") == null ? null : ((Number) body.get("ttlSeconds")).longValue();
        String clientKey = HttpUtil.clientHash(exchange);

        CreateUrlResult result = service.shorten(url, alias, ttl, clientKey);
        ShortUrl s = result.shortUrl;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("shortCode", s.getCode());
        response.put("shortUrl", publicBaseUrl + "/" + s.getCode());
        response.put("longUrl", s.getLongUrl());
        response.put("createdAt", s.getCreatedAt().toString());
        response.put("expiresAt", s.getExpiresAt() == null ? null : s.getExpiresAt().toString());
        response.put("customAlias", s.isCustomAlias());
        response.put("deduplicated", result.deduped);
        HttpUtil.sendJson(exchange, result.deduped ? 200 : 201, response);
    }

    private void handleMetadata(HttpExchange exchange, String code) throws IOException {
        ShortUrl s = service.getMetadata(code);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("shortCode", s.getCode());
        response.put("shortUrl", publicBaseUrl + "/" + s.getCode());
        response.put("longUrl", s.getLongUrl());
        response.put("createdAt", s.getCreatedAt().toString());
        response.put("expiresAt", s.getExpiresAt() == null ? null : s.getExpiresAt().toString());
        response.put("active", s.isActive());
        response.put("customAlias", s.isCustomAlias());
        HttpUtil.sendJson(exchange, 200, response);
    }

    private void handleAnalytics(HttpExchange exchange, String code) throws IOException {
        AnalyticsSummary summary = service.getAnalytics(code);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("shortCode", summary.code);
        response.put("totalClicks", summary.totalClicks);
        response.put("createdAt", summary.createdAt.toString());
        response.put("lastClickAt", summary.lastClickAt == null ? null : summary.lastClickAt.toString());
        response.put("clicksByReferrer", summary.clicksByReferrer);
        response.put("recentEvents", summary.recentEvents);
        HttpUtil.sendJson(exchange, 200, response);
    }

    private void handleDelete(HttpExchange exchange, String code) throws IOException {
        boolean deleted = service.delete(code);
        if (!deleted) {
            HttpUtil.sendError(exchange, 404, "NOT_FOUND", "No such short URL: " + code);
            return;
        }
        HttpUtil.sendNoBody(exchange, 204);
    }
}
