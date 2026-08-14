package com.schwab.shortener.repository;

import com.schwab.shortener.model.ShortUrl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the repository against a real (in-memory H2) database via Spring
 * Data JPA - not mocked. {@code @DataJpaTest} auto-configures an embedded
 * test database, replacing whatever datasource application.yml declares, so
 * no extra profile/properties file is needed here.
 */
@DataJpaTest
class ShortUrlRepositoryTest {

    @Autowired
    private ShortUrlRepository repository;

    @Test
    void savesAndRetrievesByCode() {
        ShortUrl url = ShortUrl.builder().code("ABC123").longUrl("https://example.com/x")
                .createdAt(Instant.now()).active(true).customAlias(false).build();
        repository.save(url);

        Optional<ShortUrl> found = repository.findById("ABC123");

        assertThat(found).isPresent();
        assertThat(found.get().getLongUrl()).isEqualTo("https://example.com/x");
    }

    @Test
    void findFirstByLongUrlAndActiveTrueIgnoresInactiveRows() {
        repository.save(ShortUrl.builder().code("INACTIVE1").longUrl("https://example.com/dup")
                .createdAt(Instant.now()).active(false).customAlias(false).build());
        repository.save(ShortUrl.builder().code("ACTIVE1").longUrl("https://example.com/dup")
                .createdAt(Instant.now()).active(true).customAlias(false).build());

        Optional<ShortUrl> found = repository.findFirstByLongUrlAndActiveTrue("https://example.com/dup");

        assertThat(found).isPresent();
        assertThat(found.get().getCode()).isEqualTo("ACTIVE1");
    }

    @Test
    void findByActiveTrueAndExpiresAtBeforeReturnsOnlyExpiredActiveLinks() {
        Instant now = Instant.now();
        repository.save(ShortUrl.builder().code("EXPIRED1").longUrl("https://example.com/a")
                .createdAt(now.minusSeconds(100)).expiresAt(now.minusSeconds(10)).active(true).customAlias(false).build());
        repository.save(ShortUrl.builder().code("FUTURE1").longUrl("https://example.com/b")
                .createdAt(now).expiresAt(now.plusSeconds(1000)).active(true).customAlias(false).build());
        repository.save(ShortUrl.builder().code("NOTTL1").longUrl("https://example.com/c")
                .createdAt(now).active(true).customAlias(false).build());

        List<ShortUrl> expired = repository.findByActiveTrueAndExpiresAtBefore(now);

        assertThat(expired).extracting(ShortUrl::getCode).containsExactly("EXPIRED1");
    }
}
