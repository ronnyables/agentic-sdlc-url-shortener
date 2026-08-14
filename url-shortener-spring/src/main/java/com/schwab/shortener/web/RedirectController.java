package com.schwab.shortener.web;

import com.schwab.shortener.model.ShortUrl;
import com.schwab.shortener.service.UrlShortenerService;
import com.schwab.shortener.util.ClientHashUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * The public redirect endpoint. The {code} pattern is constrained to
 * alphanumeric/underscore/hyphen (no dots) specifically so it never shadows
 * framework paths like /favicon.ico, and is registered separately from
 * /api/** and Spring Boot's own /actuator/**, /h2-console/** mappings.
 */
@RestController
public class RedirectController {

    private final UrlShortenerService service;

    public RedirectController(UrlShortenerService service) {
        this.service = service;
    }

    @GetMapping("/{code:[A-Za-z0-9_-]{1,64}}")
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        ShortUrl shortUrl = service.resolve(code);
        service.recordClick(code, request.getHeader(HttpHeaders.REFERER), request.getHeader(HttpHeaders.USER_AGENT),
                ClientHashUtil.hash(request.getRemoteAddr()));
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(shortUrl.getLongUrl())).build();
    }
}
