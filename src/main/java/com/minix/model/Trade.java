package com.minix.model;

import com.minix.util.JsonSerializable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A completed deal: buyer and seller agreed, shares and money changed hands.
 * Trades never change after they happen, so every field is final (immutable object).
 */
public class Trade implements JsonSerializable {

    private final long id;
    private final String symbol;
    private final double price;
    private final int quantity;
    private final Trader buyer;
    private final Trader seller;
    private final Side aggressorSide;   // side of the incoming order that caused the trade
    private final long time;

    public Trade(long id, String symbol, double price, int quantity,
                 Trader buyer, Trader seller, Side aggressorSide, long time) {
        this.id = id;
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.buyer = buyer;
        this.seller = seller;
        this.aggressorSide = aggressorSide;
        this.time = time;
    }

    public double getValue() {
        return Math.round(price * quantity * 100.0) / 100.0;
    }

    public long getId() { return id; }
    public String getSymbol() { return symbol; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public Trader getBuyer() { return buyer; }
    public Trader getSeller() { return seller; }
    public Side getAggressorSide() { return aggressorSide; }
    public long getTime() { return time; }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("symbol", symbol);
        m.put("price", price);
        m.put("quantity", quantity);
        m.put("buyer", buyer.getUsername());
        m.put("seller", seller.getUsername());
        m.put("side", aggressorSide);
        m.put("time", time);
        return m;
    }

    @Override
    public String toString() {
        return String.format("%s %d @ %.2f (%s <- %s)", symbol, quantity, price, buyer, seller);
    }
}
