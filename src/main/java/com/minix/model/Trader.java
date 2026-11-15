package com.minix.model;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * ABSTRACT CLASS: anyone who can trade on the exchange, human or bot.
 *
 * Money and shares are kept in two parts:
 *   - the total you own (cash, holdings)
 *   - the part locked by your open limit orders (reservedCash, reservedShares)
 * "Available" = total - reserved. This stops someone from placing two buy orders
 * that together cost more than the cash they actually have.
 */
public abstract class Trader {

    private final long id;
    private final String username;
    private double cash;
    private double reservedCash;

    // symbol -> number of shares owned  (HashMap: fast lookup by key)
    private final Map<String, Integer> holdings = new HashMap<>();
    // symbol -> shares locked by open SELL limit orders
    private final Map<String, Integer> reservedShares = new HashMap<>();
    // symbol -> average price paid per share (for profit / loss)
    private final Map<String, Double> averagePrice = new HashMap<>();

    protected Trader(long id, String username, double startingCash) {
        this.id = id;
        this.username = username;
        this.cash = startingCash;
    }

    /** Is this an automated trader? Each subclass answers differently. */
    public abstract boolean isBot();

    // ---------------- cash ----------------

    public double getAvailableCash() {
        return round2(cash - reservedCash);
    }

    public void reserveCash(double amount) {
        reservedCash = round2(reservedCash + amount);
    }

    public void releaseCash(double amount) {
        reservedCash = Math.max(0, round2(reservedCash - amount));
    }

    // ---------------- shares ----------------

    public int getShares(String symbol) {
        return holdings.getOrDefault(symbol, 0);
    }

    public int getReservedShares(String symbol) {
        return reservedShares.getOrDefault(symbol, 0);
    }

    public int getAvailableShares(String symbol) {
        return getShares(symbol) - getReservedShares(symbol);
    }

    public void reserveShares(String symbol, int qty) {
        reservedShares.merge(symbol, qty, Integer::sum);
    }

    public void releaseShares(String symbol, int qty) {
        int left = getReservedShares(symbol) - qty;
        if (left <= 0) reservedShares.remove(symbol); else reservedShares.put(symbol, left);
    }

    public double getAveragePrice(String symbol) {
        return averagePrice.getOrDefault(symbol, 0.0);
    }

    // ---------------- applying a trade ----------------

    /** Called by the engine when this trader buys qty shares at price. */
    public void applyBuy(String symbol, int qty, double price) {
        int oldQty = getShares(symbol);
        double oldCost = oldQty * getAveragePrice(symbol);
        int newQty = oldQty + qty;
        cash = round2(cash - qty * price);
        holdings.put(symbol, newQty);
        averagePrice.put(symbol, round2((oldCost + qty * price) / newQty));
    }

    /** Called by the engine when this trader sells qty shares at price. */
    public void applySell(String symbol, int qty, double price) {
        int newQty = getShares(symbol) - qty;
        cash = round2(cash + qty * price);
        if (newQty <= 0) {
            holdings.remove(symbol);
            averagePrice.remove(symbol);
        } else {
            holdings.put(symbol, newQty);   // average price does not change when selling
        }
    }

    /** Used when a saved user is loaded back from the database. */
    public void restoreHolding(String symbol, int qty, double avgPrice) {
        if (qty > 0) {
            holdings.put(symbol, qty);
            averagePrice.put(symbol, avgPrice);
        }
    }

    // ---------------- getters ----------------

    public long getId() { return id; }
    public String getUsername() { return username; }
    public double getCash() { return cash; }
    public double getReservedCash() { return reservedCash; }

    /** A sorted copy, so callers cannot change the trader's real holdings. */
    public Map<String, Integer> getHoldings() {
        return new TreeMap<>(holdings);
    }

    protected static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    @Override
    public String toString() {
        return username;
    }
}
