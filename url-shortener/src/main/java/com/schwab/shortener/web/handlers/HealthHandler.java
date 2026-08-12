package com.schwab.shortener.web.handlers;

import com.schwab.shortener.web.HttpUtil;
import com.schwab.shortener.web.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.time.Instant;

public final class HealthHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws java.io.IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendNoBody(exchange, 405);
            return;
        }
        HttpUtil.sendJson(exchange, 200, JsonUtil.object(
                "status", "UP",
                "timestamp", Instant.now().toString()));
    }
}
