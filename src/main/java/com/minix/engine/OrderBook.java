package com.minix.engine;

import com.minix.model.Order;
import com.minix.model.Side;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * The list of waiting buy and sell orders for ONE stock.
 *
 * Two PriorityQueues keep the "best" order at the front, so the engine can always look
 * at the most attractive buyer and seller in O(1) with peek():
 *   bids (buyers):  highest price first, then earliest order first
 *   asks (sellers): lowest price first,  then earliest order first
 * This rule is called PRICE-TIME PRIORITY and is what real exchanges use.
 */
public class OrderBook {

    /** Buyers: higher price is better; same price -> lower id (earlier) is better. */
    static final Comparator<Order> BID_ORDER =
            Comparator.comparingDouble(Order::getLimitPrice).reversed()
                      .thenComparingLong(Order::getId);

    /** Sellers: lower price is better; same price -> lower id (earlier) is better. */
    static final Comparator<Order> ASK_ORDER =
            Comparator.comparingDouble(Order::getLimitPrice)
                      .thenComparingLong(Order::getId);

    private final String symbol;
    private final PriorityQueue<Order> bids = new PriorityQueue<>(BID_ORDER);
    private final PriorityQueue<Order> asks = new PriorityQueue<>(ASK_ORDER);

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public void add(Order order) {
        queueFor(order.getSide()).add(order);
    }

    public boolean remove(Order order) {
        return queueFor(order.getSide()).remove(order);
    }

    /** The queue an incoming order of this side trades AGAINST. */
    public PriorityQueue<Order> oppositeOf(Side side) {
        return queueFor(side.opposite());
    }

    public PriorityQueue<Order> queueFor(Side side) {
        return side == Side.BUY ? bids : asks;
    }

    public Order bestBid() { return bids.peek(); }
    public Order bestAsk() { return asks.peek(); }

    /** Waiting sell orders in best-first order (used to price a market buy). */
    public List<Order> sortedAsks() {
        return sorted(asks, ASK_ORDER);
    }

    public List<Order> sortedBids() {
        return sorted(bids, BID_ORDER);
    }

    /**
     * Note: iterating a PriorityQueue does NOT give sorted order (only the head is
     * guaranteed to be the best), so we copy it into a list and sort that copy.
     */
    private static List<Order> sorted(PriorityQueue<Order> queue, Comparator<Order> cmp) {
        List<Order> list = new ArrayList<>(queue);
        list.sort(cmp);
        return list;
    }

    /**
     * Groups orders by price for display, e.g. three sellers at 101.00 become one row
     * "101.00 | 45 shares | 3 orders". Shows at most 'depth' price levels per side.
     */
    public Map<String, Object> snapshot(int depth) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("symbol", symbol);
        m.put("bids", levels(sortedBids(), depth));
        m.put("asks", levels(sortedAsks(), depth));
        return m;
    }

    private static List<Map<String, Object>> levels(List<Order> orders, int depth) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> current = null;
        double currentPrice = -1;
        for (Order o : orders) {
            if (current == null || o.getLimitPrice() != currentPrice) {
                if (result.size() == depth) break;
                currentPrice = o.getLimitPrice();
                current = new LinkedHashMap<>();
                current.put("price", currentPrice);
                current.put("quantity", 0);
                current.put("orders", 0);
                result.add(current);
            }
            current.put("quantity", (Integer) current.get("quantity") + o.getRemaining());
            current.put("orders", (Integer) current.get("orders") + 1);
        }
        return result;
    }

    public String getSymbol() { return symbol; }
    public int bidCount() { return bids.size(); }
    public int askCount() { return asks.size(); }
}
