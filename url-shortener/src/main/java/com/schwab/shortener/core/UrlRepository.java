package com.schwab.shortener.core;

import com.schwab.shortener.core.model.ShortUrl;
import java.util.Collection;
import java.util.Optional;

public interface UrlRepository {
    void save(ShortUrl shortUrl);
    Optional<ShortUrl> findByCode(String code);
    boolean existsByCode(String code);
    /** Returns an existing code previously issued for this exact long URL, if any (dedup). */
    Optional<String> findExistingCodeForLongUrl(String longUrl);
    boolean deleteByCode(String code);
    Collection<ShortUrl> all();
}
