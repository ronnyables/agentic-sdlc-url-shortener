package com.schwab.shortener.core.exceptions;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) { super(message); }
}
