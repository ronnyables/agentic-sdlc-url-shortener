package com.schwab.shortener.core.exceptions;

public class InvalidUrlException extends RuntimeException {
    public InvalidUrlException(String message) { super(message); }
}
