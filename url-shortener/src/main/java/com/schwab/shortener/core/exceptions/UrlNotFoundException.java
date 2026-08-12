package com.schwab.shortener.core.exceptions;

public class UrlNotFoundException extends RuntimeException {
    public UrlNotFoundException(String message) { super(message); }
}
