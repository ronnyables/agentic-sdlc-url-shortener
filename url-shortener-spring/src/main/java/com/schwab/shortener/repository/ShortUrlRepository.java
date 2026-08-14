package com.schwab.shortener.repository;

import com.schwab.shortener.model.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, String> {

    /** Used for de-duplication: reuse an existing active mapping for the same long URL. */
    Optional<ShortUrl> findFirstByLongUrlAndActiveTrue(String longUrl);

    List<ShortUrl> findByActiveTrueAndExpiresAtBefore(Instant cutoff);
}
