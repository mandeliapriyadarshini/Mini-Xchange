package com.minix.model;

/**
 * INHERITANCE: a real user who logs in through the website.
 * Their cash and holdings are saved in the database after every trade.
 */
public class HumanTrader extends Trader {

    public static final double STARTING_CASH = 100_000.00;

    public HumanTrader(long id, String username, double cash) {
        super(id, username, cash);
    }

    @Override
    public boolean isBot() {
        return false;
    }
}
