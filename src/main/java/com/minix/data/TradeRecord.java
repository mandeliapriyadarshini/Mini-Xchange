package com.minix.data;

import com.minix.util.JsonSerializable;

import java.util.LinkedHashMap;
import java.util.Map;

/** One past trade seen from a particular user's point of view (for the history page and CSV). */
public record TradeRecord(long time, String symbol, String side, int quantity,
                          double price, String counterparty) implements JsonSerializable {

    public double value() {
        return Math.round(price * quantity * 100.0) / 100.0;
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("time", time);
        m.put("symbol", symbol);
        m.put("side", side);
        m.put("quantity", quantity);
        m.put("price", price);
        m.put("value", value());
        m.put("counterparty", counterparty);
        return m;
    }
}
