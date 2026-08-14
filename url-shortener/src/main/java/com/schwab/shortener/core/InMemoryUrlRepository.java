package com.schwab.shortener.core;

import com.schwab.shortener.core.model.ShortUrl;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory repository. Chosen for the prototype so the service is a
 * self-contained, zero-external-dependency artifact (see docs/04-testing-and-tradeoffs.md
 * for the persistence trade-off discussion and the swap-in path to a real DB).
 */
public final class InMemoryUrlRepository implements UrlRepository {

    private final ConcurrentHashMap<String, ShortUrl> byCode = new ConcurrentHashMap<>();
    // reverse index for de-duplication: longUrl -> code
    private final ConcurrentHashMap<String, String> byLongUrl = new ConcurrentHashMap<>();

    @Override
    public void save(ShortUrl shortUrl) {
        byCode.put(shortUrl.getCode(), shortUrl);
        byLongUrl.putIfAbsent(shortUrl.getLongUrl(), shortUrl.getCode());
    }

    @Override
    public Optional<ShortUrl> findByCode(String code) {
        return Optional.ofNullable(byCode.get(code));
    }

    @Override
    public boolean existsByCode(String code) {
        return byCode.containsKey(code);
    }

    @Override
    public Optional<String> findExistingCodeForLongUrl(String longUrl) {
        return Optional.ofNullable(byLongUrl.get(longUrl));
    }

    @Override
    public boolean deleteByCode(String code) {
        ShortUrl removed = byCode.remove(code);
        if (removed != null) {
            byLongUrl.remove(removed.getLongUrl(), code);
            return true;
        }
        return false;
    }

    @Override
    public Collection<ShortUrl> all() {
        return byCode.values();
    }
}
