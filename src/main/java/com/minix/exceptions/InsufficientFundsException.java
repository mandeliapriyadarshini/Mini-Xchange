package com.minix.exceptions;

/** Thrown when a buyer does not have enough available cash for the order. */
public class InsufficientFundsException extends ExchangeException {

    private final double needed;
    private final double available;

    public InsufficientFundsException(double needed, double available) {
        super(String.format("Insufficient funds: this order needs %.2f but you only have %.2f available.",
                needed, available));
        this.needed = needed;
        this.available = available;
    }

    public double getNeeded() { return needed; }
    public double getAvailable() { return available; }
}
