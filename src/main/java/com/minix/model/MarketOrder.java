package com.minix.model;

/**
 * INHERITANCE: a MarketOrder IS-AN Order with no price.
 *
 * "Buy 10 DSRT now at whatever the best available price is."
 * It never waits: anything that cannot be filled immediately is cancelled.
 */
public class MarketOrder extends Order {

    public MarketOrder(Trader trader, String symbol, Side side, int quantity) {
        super(trader, symbol, side, quantity);
    }

    /**
     * A market buyer accepts any price (infinitely high), a market seller accepts any
     * price down to zero. With these values the normal "crosses" check in Order works
     * for market orders too, so the engine needs no special case.
     */
    @Override
    public double getLimitPrice() {
        return getSide() == Side.BUY ? Double.MAX_VALUE : 0.0;
    }

    @Override
    public boolean canRestInBook() {
        return false;
    }

    @Override
    public String getType() {
        return "MARKET";
    }
}
