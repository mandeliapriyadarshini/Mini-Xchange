package com.minix.exceptions;

/** Thrown when an order's details make no sense (zero quantity, unknown stock, negative price...). */
public class InvalidOrderException extends ExchangeException {
    public InvalidOrderException(String message) {
        super(message);
    }
}
