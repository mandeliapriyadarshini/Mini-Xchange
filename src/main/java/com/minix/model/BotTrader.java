package com.minix.model;

import com.minix.engine.MatchingEngine;
import com.minix.exceptions.ExchangeException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * INHERITANCE + MULTITHREADING: an automated trader that keeps the market alive.
 *
 * It extends Trader (so the engine treats it exactly like a human) and implements
 * Runnable, so it can run on its own background Thread:
 *
 *     Thread t = new Thread(bot, "Bot-1");
 *     t.start();       // run() now executes in parallel with the web server
 *
 * Every second or so it places a random buy or sell order close to the current price.
 * Prices therefore move on their own, even when nobody is clicking anything.
 */
public class BotTrader extends Trader implements Runnable {

    private static final int MAX_OPEN_ORDERS = 12;

    private final MatchingEngine engine;
    private final Random random;
    private final Deque<Order> myOrders = new ArrayDeque<>();
    private volatile boolean running = true;   // volatile: other threads see changes immediately

    public BotTrader(long id, String name, MatchingEngine engine, long seed) {
        super(id, name, 1_000_000_000.0);   // bots have (practically) unlimited money...
        this.engine = engine;
        this.random = new Random(seed);
        for (Stock s : engine.getStocks()) {
            restoreHolding(s.getSymbol(), 1_000_000, s.getOpenPrice());   // ...and shares
        }
    }

    @Override
    public boolean isBot() {
        return true;
    }

    /**
     * Fills the order book with a "ladder" of buy and sell orders around the opening
     * price, so a human placing a market order on startup always finds someone to trade with.
     */
    public void seedLiquidity() {
        for (Stock s : engine.getStocks()) {
            double p = s.getOpenPrice();
            for (int level = 1; level <= 6; level++) {
                double step = p * 0.002 * level;
                int qty = 10 + random.nextInt(40);
                tryPlace(new LimitOrder(this, s.getSymbol(), Side.BUY, qty, p - step));
                tryPlace(new LimitOrder(this, s.getSymbol(), Side.SELL, qty, p + step));
            }
        }
        myOrders.clear();   // the seeded ladder stays in the book; only random orders get recycled
    }

    /** The code that runs on the bot's own thread. */
    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(500 + random.nextInt(1000));
                tradeOnce();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();   // someone asked us to stop
                running = false;
            } catch (RuntimeException e) {
                System.err.println(getUsername() + " error: " + e.getMessage());
            }
        }
    }

    /** One random decision. Public so tests can call it without starting a thread. */
    public void tradeOnce() {
        List<Stock> stocks = engine.getStocks();
        Stock stock = stocks.get(random.nextInt(stocks.size()));
        double last = stock.getLastPrice();
        // gently pull the price back towards the opening price so it does not drift forever
        double centre = last + (stock.getOpenPrice() - last) * 0.03;
        Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
        int qty = 1 + random.nextInt(20);

        Order order;
        if (random.nextInt(8) == 0) {
            order = new MarketOrder(this, stock.getSymbol(), side, qty);
        } else {
            // a price within about +/-0.6% of the centre; sometimes it crosses and trades
            double offset = centre * (random.nextDouble() * 0.010 - 0.004);
            double price = side == Side.BUY ? centre - offset : centre + offset;
            order = new LimitOrder(this, stock.getSymbol(), side, qty, Math.max(1.0, price));
        }
        tryPlace(order);
        cancelStaleOrders();
    }

    private void tryPlace(Order order) {
        try {
            engine.placeOrder(order);
            if (order.isActive()) myOrders.addLast(order);
        } catch (ExchangeException e) {
            // a bot does not care if an order is rejected (e.g. no sellers yet); just skip it
        }
    }

    /** Keep at most MAX_OPEN_ORDERS of our own random orders in the book: cancel the oldest. */
    private void cancelStaleOrders() {
        myOrders.removeIf(o -> !o.isActive());
        while (myOrders.size() > MAX_OPEN_ORDERS) {
            Order oldest = myOrders.pollFirst();
            try {
                engine.cancelOrder(oldest.getId(), this);
            } catch (ExchangeException ignored) {
                // it was filled in the meantime, nothing to cancel
            }
        }
    }

    public void stop() {
        running = false;
    }
}
