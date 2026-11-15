package com.minix.data;

import com.minix.exceptions.AuthException;
import com.minix.model.Trade;
import com.minix.model.Trader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A second implementation of Storage that keeps everything in memory (lost on restart).
 * Used by tests and as a fallback when the SQLite driver is not available, so the
 * exchange still runs. This shows POLYMORPHISM: Main can use either class as a "Storage".
 */
public class InMemoryStorage implements Storage {

    private final Map<String, UserRecord> users = new HashMap<>();
    private final List<Trade> trades = new ArrayList<>();
    private long nextId = 1;

    @Override
    public void init() {
        // nothing to prepare
    }

    @Override
    public synchronized UserRecord createUser(String username, String passwordHash, double startingCash)
            throws AuthException {
        if (users.containsKey(username)) {
            throw new AuthException("Username '" + username + "' is already taken.");
        }
        UserRecord u = new UserRecord(nextId++, username, passwordHash, startingCash, List.of());
        users.put(username, u);
        return u;
    }

    @Override
    public synchronized UserRecord findUser(String username) {
        return users.get(username);
    }

    @Override
    public synchronized void saveTraderState(Trader trader) {
        UserRecord old = users.get(trader.getUsername());
        if (old == null) return;
        List<HoldingRecord> holdings = new ArrayList<>();
        for (Map.Entry<String, Integer> h : trader.getHoldings().entrySet()) {
            holdings.add(new HoldingRecord(h.getKey(), h.getValue(), trader.getAveragePrice(h.getKey())));
        }
        users.put(trader.getUsername(),
                new UserRecord(old.id(), old.username(), old.passwordHash(), trader.getCash(), holdings));
    }

    @Override
    public synchronized void saveTrade(Trade trade) {
        trades.add(trade);
    }

    @Override
    public synchronized List<TradeRecord> findTradesForUser(long userId, int limit) {
        List<TradeRecord> list = new ArrayList<>();
        for (int i = trades.size() - 1; i >= 0 && list.size() < limit; i--) {
            Trade t = trades.get(i);
            boolean bought = !t.getBuyer().isBot() && t.getBuyer().getId() == userId;
            boolean sold = !t.getSeller().isBot() && t.getSeller().getId() == userId;
            if (bought || sold) {
                list.add(new TradeRecord(t.getTime(), t.getSymbol(), bought ? "BUY" : "SELL",
                        t.getQuantity(), t.getPrice(),
                        bought ? t.getSeller().getUsername() : t.getBuyer().getUsername()));
            }
        }
        return list;
    }

    @Override
    public String describe() {
        return "in-memory storage (data is NOT saved when the server stops)";
    }
}
