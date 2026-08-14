package com.schwab.shortener.web;

import com.schwab.shortener.exception.AliasConflictException;
import com.schwab.shortener.exception.InvalidUrlException;
import com.schwab.shortener.exception.UrlNotFoundException;
import com.schwab.shortener.model.ShortUrl;
import com.schwab.shortener.service.AnalyticsSummary;
import com.schwab.shortener.service.CreateUrlResult;
import com.schwab.shortener.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-slice test: only the controller layer + Spring MVC infrastructure is
 * loaded (no real database), with the service layer mocked - verifies
 * routing, status codes, JSON shape, and exception-to-status mapping.
 */
@WebMvcTest({UrlController.class, RedirectController.class, GlobalExceptionHandler.class})
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlShortenerService service;

    private ShortUrl sampleUrl() {
        return ShortUrl.builder().code("abc123").longUrl("https://example.com/page")
                .createdAt(Instant.now()).active(true).customAlias(false).build();
    }

    @Test
    void createReturns201WithShortUrl() throws Exception {
        when(service.shorten(anyString(), any(), any(), anyString()))
                .thenReturn(new CreateUrlResult(sampleUrl(), false));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/page\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc123"))
                .andExpect(jsonPath("$.longUrl").value("https://example.com/page"));
    }

    @Test
    void createWithBlankUrlReturns400() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void createWithInvalidUrlReturns400FromService() throws Exception {
        when(service.shorten(anyString(), any(), any(), anyString()))
                .thenThrow(new InvalidUrlException("Malformed URL"));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"not-a-url-but-not-blank\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_URL"));
    }

    @Test
    void createWithConflictingAliasReturns409() throws Exception {
        when(service.shorten(anyString(), anyString(), any(), anyString()))
                .thenThrow(new AliasConflictException("Alias already in use: taken"));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/x\",\"customAlias\":\"taken\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALIAS_CONFLICT"));
    }

    @Test
    void metadataReturns404ForUnknownCode() throws Exception {
        when(service.getMetadata("nope")).thenThrow(new UrlNotFoundException("No such short URL: nope"));

        mockMvc.perform(get("/api/urls/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void analyticsReturnsAggregatedCounts() throws Exception {
        when(service.getAnalytics("abc123")).thenReturn(new AnalyticsSummary(
                "abc123", 3L, Instant.now(), Instant.now(), Map.of("direct", 3L), java.util.List.of()));

        mockMvc.perform(get("/api/urls/abc123/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(3));
    }

    @Test
    void deleteReturns204WhenFound() throws Exception {
        when(service.delete("abc123")).thenReturn(true);
        mockMvc.perform(delete("/api/urls/abc123")).andExpect(status().isNoContent());
    }

    @Test
    void deleteReturns404WhenNotFound() throws Exception {
        when(service.delete("nope")).thenReturn(false);
        mockMvc.perform(delete("/api/urls/nope")).andExpect(status().isNotFound());
    }

    @Test
    void redirectReturns302WithLocationHeader() throws Exception {
        when(service.resolve("abc123")).thenReturn(sampleUrl());

        mockMvc.perform(get("/abc123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/page"));
    }
}
