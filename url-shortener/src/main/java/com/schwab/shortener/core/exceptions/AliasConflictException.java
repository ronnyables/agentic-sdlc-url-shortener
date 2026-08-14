package com.schwab.shortener.core.exceptions;

public class AliasConflictException extends RuntimeException {
    public AliasConflictException(String message) { super(message); }
}
