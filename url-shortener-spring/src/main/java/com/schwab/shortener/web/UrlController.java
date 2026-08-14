package com.schwab.shortener.web;

import com.schwab.shortener.model.ShortUrl;
import com.schwab.shortener.service.AnalyticsSummary;
import com.schwab.shortener.service.CreateUrlResult;
import com.schwab.shortener.service.UrlShortenerService;
import com.schwab.shortener.util.ClientHashUtil;
import com.schwab.shortener.web.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/urls")
public class UrlController {

    private final UrlShortenerService service;
    private final String publicBaseUrl;

    public UrlController(UrlShortenerService service, @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.service = service;
        this.publicBaseUrl = publicBaseUrl;
    }

    @PostMapping
    public ResponseEntity<CreateUrlResponse> create(@Valid @RequestBody CreateUrlRequest request, HttpServletRequest httpRequest) {
        String clientKey = ClientHashUtil.hash(httpRequest.getRemoteAddr());
        CreateUrlResult result = service.shorten(request.url(), request.customAlias(), request.ttlSeconds(), clientKey);
        ShortUrl s = result.shortUrl();
        CreateUrlResponse body = new CreateUrlResponse(
                s.getCode(), publicBaseUrl + "/" + s.getCode(), s.getLongUrl(),
                s.getCreatedAt(), s.getExpiresAt(), s.isCustomAlias(), result.deduped());
        return ResponseEntity.status(result.deduped() ? HttpStatus.OK : HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{code}")
    public MetadataResponse metadata(@PathVariable String code) {
        ShortUrl s = service.getMetadata(code);
        return new MetadataResponse(s.getCode(), publicBaseUrl + "/" + s.getCode(), s.getLongUrl(),
                s.getCreatedAt(), s.getExpiresAt(), s.isActive(), s.isCustomAlias());
    }

    @GetMapping("/{code}/analytics")
    public AnalyticsResponse analytics(@PathVariable String code) {
        AnalyticsSummary summary = service.getAnalytics(code);
        return new AnalyticsResponse(summary.code(), summary.totalClicks(), summary.createdAt(),
                summary.lastClickAt(), summary.clicksByReferrer(), summary.recentEvents());
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        boolean deleted = service.delete(code);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
