package com.schwab.shortener.core.exceptions;

public class UrlExpiredException extends RuntimeException {
    public UrlExpiredException(String message) { super(message); }
}
