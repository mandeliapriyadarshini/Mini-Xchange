package com.minix.exceptions;

/** Thrown when a seller tries to sell more shares than they own (and have not already listed). */
public class InsufficientSharesException extends ExchangeException {
    public InsufficientSharesException(String symbol, int wanted, int available) {
        super("Insufficient shares: you tried to sell " + wanted + " " + symbol
                + " but only " + available + " are available.");
    }
}
