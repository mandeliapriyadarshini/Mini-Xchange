package com.minix.model;

import com.minix.util.JsonSerializable;

import java.util.LinkedHashMap;
import java.util.Map;

/** One point on the price chart: the time of a trade and the price it happened at. */
public class PricePoint implements JsonSerializable {

    private final long time;
    private final double price;

    public PricePoint(long time, double price) {
        this.time = time;
        this.price = price;
    }

    public long getTime() { return time; }
    public double getPrice() { return price; }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", time);
        m.put("p", price);
        return m;
    }
}
