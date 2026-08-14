package com.schwab.shortener.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

public final class HttpUtil {

    private HttpUtil() { }

    public static void sendJson(HttpExchange exchange, int status, Object bodyObject) throws IOException {
        byte[] bytes = JsonUtil.write(bodyObject).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void sendError(HttpExchange exchange, int status, String code, String message) throws IOException {
        sendJson(exchange, status, JsonUtil.object("error", code, "message", message));
    }

    public static void sendNoBody(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.getResponseBody().close();
    }

    public static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = is.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
            return buf.toString(StandardCharsets.UTF_8);
        }
    }

    public static String[] pathSegments(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        String trimmed = path.replaceAll("^/+", "").replaceAll("/+$", "");
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/");
    }

    public static String header(HttpExchange exchange, String name, String fallback) {
        String v = exchange.getRequestHeaders().getFirst(name);
        return v == null || v.isBlank() ? fallback : v;
    }

    /** Hashes the remote client address for privacy-preserving analytics (never store raw IPs). */
    public static String clientHash(HttpExchange exchange) {
        String remote = exchange.getRemoteAddress() == null ? "unknown"
                : exchange.getRemoteAddress().getAddress().getHostAddress();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(remote.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "unhashed";
        }
    }

    public static Object requireString(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) throw new IllegalArgumentException("Missing required field: " + key);
        return v;
    }
}
