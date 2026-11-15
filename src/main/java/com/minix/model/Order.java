package com.minix.model;

import com.minix.exceptions.InvalidOrderException;
import com.minix.util.JsonSerializable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ABSTRACT CLASS: the common parent of every order type.
 *
 * It holds what all orders share (who, which stock, buy/sell, how many) and leaves
 * the order-specific behaviour to subclasses through abstract methods. The matching
 * engine only ever works with the type "Order", so it never needs to know whether it
 * is holding a LimitOrder or a MarketOrder. That is POLYMORPHISM.
 */
public abstract class Order implements Validatable, JsonSerializable {

    /**
     * Shared counter for all orders. Every new order gets the next number, so a lower id
     * always means "placed earlier". The order book uses this to break ties between two
     * orders at the same price (time priority). AtomicLong is safe across threads.
     */
    private static final AtomicLong NEXT_ID = new AtomicLong(1);

    private final long id;
    private final Trader trader;
    private final String symbol;
    private final Side side;
    private final int quantity;
    private int remaining;
    private final long createdAt;
    private OrderStatus status;

    protected Order(Trader trader, String symbol, Side side, int quantity) {
        this.id = NEXT_ID.getAndIncrement();
        this.trader = trader;
        this.symbol = symbol == null ? null : symbol.trim().toUpperCase();
        this.side = side;
        this.quantity = quantity;
        this.remaining = quantity;
        this.createdAt = System.currentTimeMillis();
        this.status = OrderStatus.OPEN;
    }

    // ---------- abstract methods: each order type answers these differently ----------

    /** The worst price this order accepts. Used by the engine to decide if two orders cross. */
    public abstract double getLimitPrice();

    /** Can the unfilled part wait in the order book? (limit: yes, market: no) */
    public abstract boolean canRestInBook();

    /** Short name shown on the website, e.g. "LIMIT". */
    public abstract String getType();

    // ---------- shared behaviour ----------

    /** Basic checks that apply to every order. Subclasses call super.validate() and add more. */
    @Override
    public void validate() throws InvalidOrderException {
        if (trader == null) throw new InvalidOrderException("Order has no trader.");
        if (symbol == null || symbol.isEmpty()) throw new InvalidOrderException("Please choose a stock.");
        if (side == null) throw new InvalidOrderException("Please choose BUY or SELL.");
        if (quantity <= 0) throw new InvalidOrderException("Quantity must be at least 1.");
        if (quantity > 10_000) throw new InvalidOrderException("Maximum 10,000 shares per order.");
    }

    /**
     * Does this (incoming) order cross the resting order on the other side?
     * A buyer crosses a seller when the buyer will pay at least what the seller asks.
     */
    public boolean crosses(Order resting) {
        if (side == Side.BUY) {
            return getLimitPrice() >= resting.getLimitPrice();
        }
        return getLimitPrice() <= resting.getLimitPrice();
    }

    /** Record that qty shares of this order have traded. */
    public void fill(int qty) {
        if (qty <= 0 || qty > remaining) {
            throw new IllegalArgumentException("Bad fill quantity " + qty + " (remaining " + remaining + ")");
        }
        remaining -= qty;
        status = remaining == 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

    /** Remove the order: whatever has not traded yet will never trade. */
    public void cancel() {
        status = OrderStatus.CANCELLED;
    }

    public int getFilledQuantity() {
        return quantity - remaining;
    }

    public boolean isActive() {
        return status == OrderStatus.OPEN || status == OrderStatus.PARTIALLY_FILLED;
    }

    // ---------- getters (encapsulation: fields are private, read through methods) ----------

    public long getId() { return id; }
    public Trader getTrader() { return trader; }
    public String getSymbol() { return symbol; }
    public Side getSide() { return side; }
    public int getQuantity() { return quantity; }
    public int getRemaining() { return remaining; }
    public long getCreatedAt() { return createdAt; }
    public OrderStatus getStatus() { return status; }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("symbol", symbol);
        m.put("side", side);
        m.put("type", getType());
        m.put("price", canRestInBook() ? getLimitPrice() : null);
        m.put("quantity", quantity);
        m.put("filled", getFilledQuantity());
        m.put("remaining", remaining);
        m.put("status", status);
        m.put("createdAt", createdAt);
        return m;
    }

    @Override
    public String toString() {
        return getType() + " " + side + " " + quantity + " " + symbol + " (#" + id + ")";
    }
}
