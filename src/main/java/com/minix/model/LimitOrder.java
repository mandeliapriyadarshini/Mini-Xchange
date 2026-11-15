package com.minix.model;

import com.minix.exceptions.InvalidOrderException;

/**
 * INHERITANCE: a LimitOrder IS-AN Order with a price attached.
 *
 * "Buy 10 DSRT at 100 or less." If no seller is that cheap right now,
 * the order waits in the order book until one appears (or the user cancels it).
 */
public class LimitOrder extends Order {

    private final double price;

    public LimitOrder(Trader trader, String symbol, Side side, int quantity, double price) {
        super(trader, symbol, side, quantity);   // let the parent set the common fields
        this.price = Math.round(price * 100.0) / 100.0;  // keep 2 decimal places
    }

    @Override
    public double getLimitPrice() {
        return price;
    }

    @Override
    public boolean canRestInBook() {
        return true;
    }

    @Override
    public String getType() {
        return "LIMIT";
    }

    /** Overrides the parent's checks and adds one more: the price must make sense. */
    @Override
    public void validate() throws InvalidOrderException {
        super.validate();
        if (price <= 0) throw new InvalidOrderException("Limit price must be greater than 0.");
        if (price > 1_000_000) throw new InvalidOrderException("Limit price is unrealistically high.");
    }
}
