package com.minix.engine;

import com.minix.model.Order;
import com.minix.model.Trade;
import com.minix.util.JsonSerializable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What happened to an order the moment it was placed: the trades it caused and its final state. */
public class OrderResult implements JsonSerializable {

    private final Order order;
    private final List<Trade> trades;

    public OrderResult(Order order, List<Trade> trades) {
        this.order = order;
        this.trades = List.copyOf(trades);
    }

    public Order getOrder() { return order; }
    public List<Trade> getTrades() { return trades; }

    public int getFilledQuantity() {
        int total = 0;
        for (Trade t : trades) total += t.getQuantity();
        return total;
    }

    public double getAveragePrice() {
        int qty = getFilledQuantity();
        if (qty == 0) return 0;
        double value = 0;
        for (Trade t : trades) value += t.getPrice() * t.getQuantity();
        return Math.round(value / qty * 100.0) / 100.0;
    }

    /** A friendly sentence for the website, e.g. "Partially filled: 6 of 10 at avg 101.20". */
    public String getMessage() {
        int filled = getFilledQuantity();
        int total = order.getQuantity();
        String avg = String.format("%.2f", getAveragePrice());
        if (filled == total) {
            return "Filled: " + filled + " " + order.getSymbol() + " at avg " + avg;
        }
        if (filled > 0 && order.canRestInBook()) {
            return "Partially filled: " + filled + " of " + total + " at avg " + avg
                    + ". The rest is waiting in the order book.";
        }
        if (filled > 0) {
            return "Partially filled: " + filled + " of " + total + " at avg " + avg
                    + ". Not enough orders on the other side, the rest was cancelled.";
        }
        if (order.canRestInBook()) {
            return "Order placed: no match yet, it is waiting in the order book.";
        }
        return "Nothing filled: no orders on the other side.";
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", getMessage());
        m.put("filled", getFilledQuantity());
        m.put("avgPrice", getAveragePrice());
        m.put("order", order);
        m.put("trades", trades);
        return m;
    }
}
