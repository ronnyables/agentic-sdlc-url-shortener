package com.schwab.shortener.core;

import com.schwab.shortener.core.model.ShortUrl;

public final class CreateUrlResult {
    public final ShortUrl shortUrl;
    public final boolean deduped; // true if an existing mapping was reused

    public CreateUrlResult(ShortUrl shortUrl, boolean deduped) {
        this.shortUrl = shortUrl;
        this.deduped = deduped;
    }
}
