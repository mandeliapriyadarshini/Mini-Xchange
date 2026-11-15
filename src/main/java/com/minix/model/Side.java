package com.minix.model;

/**
 * Which side of the market an order is on.
 * An enum is used instead of a String so a typo like "BYU" is impossible.
 */
public enum Side {
    BUY,
    SELL;

    /** The side this order trades against: buyers match with sellers and vice versa. */
    public Side opposite() {
        return this == BUY ? SELL : BUY;
    }
}
