package com.minix.exceptions;

/** Thrown for login / registration problems (wrong password, username taken, not logged in). */
public class AuthException extends ExchangeException {
    public AuthException(String message) {
        super(message);
    }
}
