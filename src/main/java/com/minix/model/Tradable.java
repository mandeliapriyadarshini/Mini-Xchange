package com.minix.model;

/**
 * INTERFACE: something that can be bought and sold on the exchange and has a market price.
 * Stock implements it today; a future Crypto or Bond class could implement it too,
 * and the rest of the code would work with it unchanged.
 */
public interface Tradable {

    String getSymbol();

    String getName();

    double getLastPrice();

    /** Called after every trade: the last traded price becomes the new market price. */
    void recordTrade(double price, int quantity);
}
