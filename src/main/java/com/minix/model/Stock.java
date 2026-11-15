package com.minix.model;

import com.minix.util.JsonSerializable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A company listed on Mini-Xchange.
 *
 * Nobody "sets" the price. lastPrice is simply the price of the most recent trade,
 * so it moves up and down as buyers and sellers agree on new prices.
 */
public class Stock implements Tradable, JsonSerializable {

    private static final int MAX_HISTORY = 300;   // points kept for the chart

    private final String symbol;
    private final String name;
    private final double openPrice;
    private double lastPrice;
    private double dayHigh;
    private double dayLow;
    private long volume;
    // LinkedList: cheap to add at the end and remove from the front
    private final LinkedList<PricePoint> history = new LinkedList<>();

    public Stock(String symbol, String name, double openPrice) {
        this.symbol = symbol.toUpperCase();
        this.name = name;
        this.openPrice = openPrice;
        this.lastPrice = openPrice;
        this.dayHigh = openPrice;
        this.dayLow = openPrice;
        history.add(new PricePoint(System.currentTimeMillis(), openPrice));
    }

    @Override
    public void recordTrade(double price, int quantity) {
        lastPrice = price;
        dayHigh = Math.max(dayHigh, price);
        dayLow = Math.min(dayLow, price);
        volume += quantity;
        history.add(new PricePoint(System.currentTimeMillis(), price));
        if (history.size() > MAX_HISTORY) {
            history.removeFirst();
        }
    }

    /** % change since the exchange opened. */
    public double getChangePercent() {
        return Math.round((lastPrice - openPrice) / openPrice * 10000.0) / 100.0;
    }

    @Override public String getSymbol() { return symbol; }
    @Override public String getName() { return name; }
    @Override public double getLastPrice() { return lastPrice; }
    public double getOpenPrice() { return openPrice; }
    public double getDayHigh() { return dayHigh; }
    public double getDayLow() { return dayLow; }
    public long getVolume() { return volume; }

    public List<PricePoint> getHistory() {
        return new ArrayList<>(history);
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("symbol", symbol);
        m.put("name", name);
        m.put("price", lastPrice);
        m.put("open", openPrice);
        m.put("high", dayHigh);
        m.put("low", dayLow);
        m.put("changePct", getChangePercent());
        m.put("volume", volume);
        return m;
    }
}
