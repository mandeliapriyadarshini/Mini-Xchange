package com.minix.engine;

import com.minix.exceptions.ExchangeException;
import com.minix.exceptions.InsufficientFundsException;
import com.minix.exceptions.InsufficientSharesException;
import com.minix.exceptions.InvalidOrderException;
import com.minix.model.LimitOrder;
import com.minix.model.Order;
import com.minix.model.Side;
import com.minix.model.Stock;
import com.minix.model.Trade;
import com.minix.model.Trader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * THE HEART OF THE PROJECT: receives orders, matches buyers with sellers, creates trades.
 *
 * Thread safety: orders arrive from several threads at once (website requests and the
 * bot threads). Every method that reads or changes the order books is "synchronized",
 * so only one thread can be inside the engine at a time. Without this, two threads
 * could fill the same order twice or corrupt a PriorityQueue.
 */
public class MatchingEngine {

    private static final int RECENT_TRADES_KEPT = 50;

    private final Map<String, Stock> stocks = new LinkedHashMap<>();       // keeps insertion order
    private final Map<String, OrderBook> books = new LinkedHashMap<>();
    private final Map<Long, Order> openOrders = new LinkedHashMap<>();     // order id -> order
    private final Deque<Trade> recentTrades = new ArrayDeque<>();
    private final List<TradeListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong nextTradeId = new AtomicLong(1);

    // ======================================================================
    //  Setup
    // ======================================================================

    public synchronized void addStock(Stock stock) {
        stocks.put(stock.getSymbol(), stock);
        books.put(stock.getSymbol(), new OrderBook(stock.getSymbol()));
    }

    public void addListener(TradeListener listener) {
        listeners.add(listener);
    }

    // ======================================================================
    //  Placing an order
    // ======================================================================

    /**
     * The full journey of an order:
     *   1. validate()            - is the order itself sensible?
     *   2. checkAndReserve()     - can the trader afford it? lock the cash / shares
     *   3. match()               - trade against the other side while prices cross
     *   4. leftover              - limit: wait in the book, market: cancel the rest
     *
     * @throws InvalidOrderException       bad details or unknown stock
     * @throws InsufficientFundsException  buyer cannot afford it
     * @throws InsufficientSharesException seller does not own enough shares
     */
    public synchronized OrderResult placeOrder(Order order) throws ExchangeException {
        order.validate();
        Stock stock = requireStock(order.getSymbol());
        OrderBook book = books.get(stock.getSymbol());

        checkAndReserve(order, book);

        List<Trade> trades = match(order, book, stock);

        if (order.getRemaining() > 0) {
            if (order.canRestInBook()) {
                book.add(order);
                openOrders.put(order.getId(), order);
            } else {
                order.cancel();   // market order: nothing left to trade against
            }
        }
        return new OrderResult(order, trades);
    }

    /**
     * Make sure the trader can actually pay (or deliver the shares) BEFORE anything trades.
     * For limit orders the money / shares are reserved so they cannot be spent twice.
     */
    private void checkAndReserve(Order order, OrderBook book) throws ExchangeException {
        Trader trader = order.getTrader();
        String symbol = order.getSymbol();

        if (order.getSide() == Side.BUY) {
            double cost;
            if (order instanceof LimitOrder) {
                cost = round2(order.getLimitPrice() * order.getQuantity());
            } else {
                if (book.askCount() == 0) {
                    throw new InvalidOrderException("No sellers for " + symbol + " right now. Try a limit order.");
                }
                cost = estimateMarketBuyCost(book, order.getQuantity());
            }
            if (trader.getAvailableCash() < cost) {
                throw new InsufficientFundsException(cost, trader.getAvailableCash());
            }
            if (order instanceof LimitOrder) {
                trader.reserveCash(cost);
            }
        } else {
            int available = trader.getAvailableShares(symbol);
            if (available < order.getQuantity()) {
                throw new InsufficientSharesException(symbol, order.getQuantity(), available);
            }
            if (order.getSide() == Side.SELL && book.bidCount() == 0 && !order.canRestInBook()) {
                throw new InvalidOrderException("No buyers for " + symbol + " right now. Try a limit order.");
            }
            if (order instanceof LimitOrder) {
                trader.reserveShares(symbol, order.getQuantity());
            }
        }
    }

    /** Walk the sellers from cheapest upwards and add up what qty shares would cost. */
    private double estimateMarketBuyCost(OrderBook book, int qty) {
        double cost = 0;
        int needed = qty;
        for (Order ask : book.sortedAsks()) {
            int take = Math.min(needed, ask.getRemaining());
            cost += take * ask.getLimitPrice();
            needed -= take;
            if (needed == 0) break;
        }
        return round2(cost);
    }

    /**
     * THE MATCHING LOOP.
     * While the incoming order still wants shares AND the best order on the other side
     * crosses its price, trade as many shares as both can, at the RESTING order's price
     * (the order that was there first sets the price).
     */
    private List<Trade> match(Order incoming, OrderBook book, Stock stock) {
        List<Trade> trades = new ArrayList<>();
        PriorityQueue<Order> opposite = book.oppositeOf(incoming.getSide());

        while (incoming.getRemaining() > 0 && !opposite.isEmpty()) {
            Order best = opposite.peek();
            if (!incoming.crosses(best)) {
                break;   // best price on the other side is too far away: stop
            }
            int qty = Math.min(incoming.getRemaining(), best.getRemaining());
            double price = best.getLimitPrice();

            trades.add(execute(incoming, best, qty, price, stock));

            if (best.getRemaining() == 0) {
                opposite.poll();               // fully filled: remove from the book
                openOrders.remove(best.getId());
            }
        }
        return trades;
    }

    /** Move the money and the shares between the two traders and record the trade. */
    private Trade execute(Order incoming, Order resting, int qty, double price, Stock stock) {
        Order buyOrder = incoming.getSide() == Side.BUY ? incoming : resting;
        Order sellOrder = incoming.getSide() == Side.SELL ? incoming : resting;
        Trader buyer = buyOrder.getTrader();
        Trader seller = sellOrder.getTrader();
        String symbol = stock.getSymbol();

        // Free the part of the reservation used by this fill. A limit buyer reserved
        // limitPrice per share but may pay less (the resting price), so the difference
        // goes back to their available cash automatically.
        if (buyOrder instanceof LimitOrder) {
            buyer.releaseCash(round2(buyOrder.getLimitPrice() * qty));
        }
        if (sellOrder instanceof LimitOrder) {
            seller.releaseShares(symbol, qty);
        }

        buyer.applyBuy(symbol, qty, price);
        seller.applySell(symbol, qty, price);
        incoming.fill(qty);
        resting.fill(qty);
        stock.recordTrade(price, qty);

        Trade trade = new Trade(nextTradeId.getAndIncrement(), symbol, price, qty,
                buyer, seller, incoming.getSide(), System.currentTimeMillis());

        recentTrades.addFirst(trade);
        if (recentTrades.size() > RECENT_TRADES_KEPT) recentTrades.removeLast();

        for (TradeListener listener : listeners) {
            listener.onTrade(trade);   // database, live feed, ...
        }
        return trade;
    }

    // ======================================================================
    //  Cancelling
    // ======================================================================

    public synchronized Order cancelOrder(long orderId, Trader requester) throws InvalidOrderException {
        Order order = openOrders.get(orderId);
        if (order == null) {
            throw new InvalidOrderException("Order #" + orderId + " is not open (already filled or cancelled).");
        }
        if (order.getTrader() != requester) {
            throw new InvalidOrderException("You can only cancel your own orders.");
        }
        books.get(order.getSymbol()).remove(order);
        openOrders.remove(orderId);

        // give back whatever is still reserved for the unfilled part
        if (order.getSide() == Side.BUY) {
            requester.releaseCash(round2(order.getLimitPrice() * order.getRemaining()));
        } else {
            requester.releaseShares(order.getSymbol(), order.getRemaining());
        }
        order.cancel();
        return order;
    }

    // ======================================================================
    //  Read-only views (copies, so callers cannot change engine data)
    // ======================================================================

    public synchronized List<Order> getOpenOrders(Trader trader) {
        List<Order> mine = new ArrayList<>();
        for (Order o : openOrders.values()) {
            if (o.getTrader() == trader) mine.add(o);
        }
        return mine;
    }

    public synchronized Map<String, Object> getOrderBookSnapshot(String symbol, int depth) throws InvalidOrderException {
        requireStock(symbol);
        return books.get(symbol.toUpperCase()).snapshot(depth);
    }

    public synchronized List<Trade> getRecentTrades() {
        return new ArrayList<>(recentTrades);
    }

    public synchronized List<Stock> getStocks() {
        return new ArrayList<>(stocks.values());
    }

    public synchronized Stock getStock(String symbol) throws InvalidOrderException {
        return requireStock(symbol);
    }

    private Stock requireStock(String symbol) throws InvalidOrderException {
        Stock stock = symbol == null ? null : stocks.get(symbol.toUpperCase());
        if (stock == null) {
            throw new InvalidOrderException("Unknown stock: " + symbol);
        }
        return stock;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
