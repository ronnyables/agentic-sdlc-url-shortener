package com.schwab.shortener;

import com.schwab.shortener.web.dto.CreateUrlResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack test: real embedded servlet container + real H2 database + real
 * HTTP calls via TestRestTemplate - the Spring Boot equivalent of the
 * reference build's HttpApiIntegrationTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "app.rate-limit.capacity=1000",
        "app.rate-limit.refill-per-second=1000"
})
class UrlShortenerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void healthEndpointReportsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl() + "/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void createRedirectAnalyticsRoundTrip() {
        CreateUrlResponse created = restTemplate.postForObject(
                baseUrl() + "/api/urls", java.util.Map.of("url", "https://example.com/integration"), CreateUrlResponse.class);
        assertThat(created).isNotNull();
        assertThat(created.shortCode()).isNotBlank();

        ResponseEntity<Void> redirect = restTemplate.getForEntity(baseUrl() + "/" + created.shortCode(), Void.class);
        assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirect.getHeaders().getLocation()).hasToString("https://example.com/integration");

        ResponseEntity<String> analytics = restTemplate.getForEntity(
                baseUrl() + "/api/urls/" + created.shortCode() + "/analytics", String.class);
        assertThat(analytics.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(analytics.getBody()).contains("\"totalClicks\":1");
    }

    @Test
    void unknownCodeReturns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl() + "/doesnotexist", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
