package com.schwab.shortener.service;

import com.schwab.shortener.model.ShortUrl;

public record CreateUrlResult(ShortUrl shortUrl, boolean deduped) { }
