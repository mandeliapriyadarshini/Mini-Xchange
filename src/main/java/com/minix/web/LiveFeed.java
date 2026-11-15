package com.minix.web;

import com.minix.engine.MatchingEngine;
import com.minix.engine.TradeListener;
import com.minix.exceptions.InvalidOrderException;
import com.minix.model.Stock;
import com.minix.model.Trade;
import com.minix.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * LIVE UPDATES with Server-Sent Events (SSE).
 *
 * Each browser opens ONE long-lived connection to /api/stream and keeps it open.
 * Twice a second a background thread sends every browser the latest prices, order
 * books and new trades. In JavaScript this is received with `new EventSource(...)`.
 *
 * LiveFeed is also a TradeListener: the engine hands it every trade, and it queues
 * them up until the next broadcast.
 */
public class LiveFeed implements HttpHandler, TradeListener {

    private static final int BOOK_DEPTH = 8;

    private final MatchingEngine engine;
    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<Trade> pendingTrades = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "live-feed");
        t.setDaemon(true);
        return t;
    });

    /** One connected browser. The latch keeps its HTTP request open until it disconnects. */
    private static class Client {
        final OutputStream out;
        final CountDownLatch closed = new CountDownLatch(1);
        Client(OutputStream out) { this.out = out; }
    }

    public LiveFeed(MatchingEngine engine) {
        this.engine = engine;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::broadcast, 500, 500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onTrade(Trade trade) {
        pendingTrades.add(trade);   // called inside the engine: just queue it, do no slow work here
    }

    /** A browser connects: send headers, then wait here until it goes away. */
    @Override
    public void handle(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, 0);   // 0 = response length unknown, keep streaming
        Client client = new Client(ex.getResponseBody());
        clients.add(client);
        try {
            sendTo(client, buildMessage(engine.getRecentTrades()));   // full picture straight away
            client.closed.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            clients.remove(client);
            ex.close();
        }
    }

    private void broadcast() {
        try {
            if (clients.isEmpty()) {
                pendingTrades.clear();
                return;
            }
            List<Trade> newTrades = new ArrayList<>();
            Trade t;
            while ((t = pendingTrades.poll()) != null) newTrades.add(0, t);   // newest first
            String message = buildMessage(newTrades);
            for (Client c : clients) sendTo(c, message);
        } catch (RuntimeException e) {
            System.err.println("Live feed error: " + e.getMessage());   // keep the scheduler alive
        }
    }

    private String buildMessage(List<Trade> trades) {
        // hold the engine's lock while reading, so prices and books form one consistent picture
        synchronized (engine) {
            return buildMessageLocked(trades);
        }
    }

    private String buildMessageLocked(List<Trade> trades) {
        Map<String, Object> msg = new LinkedHashMap<>();
        List<Stock> stocks = engine.getStocks();
        msg.put("stocks", stocks);
        Map<String, Object> books = new LinkedHashMap<>();
        for (Stock s : stocks) {
            try {
                books.put(s.getSymbol(), engine.getOrderBookSnapshot(s.getSymbol(), BOOK_DEPTH));
            } catch (InvalidOrderException ignored) {
                // cannot happen: the symbol comes from the engine itself
            }
        }
        msg.put("books", books);
        msg.put("trades", trades);
        msg.put("time", System.currentTimeMillis());
        return Json.stringify(msg);
    }

    private void sendTo(Client c, String json) {
        try {
            // SSE format: "data: <text>" followed by an empty line
            c.out.write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
            c.out.flush();
        } catch (IOException e) {
            c.closed.countDown();   // browser closed the tab: release its request thread
        }
    }

    public int clientCount() {
        return clients.size();
    }
}
