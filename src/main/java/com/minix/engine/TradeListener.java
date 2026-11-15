package com.minix.engine;

import com.minix.model.Trade;

/**
 * INTERFACE (observer pattern): "tell me whenever a trade happens".
 *
 * The engine keeps a list of TradeListeners and calls onTrade() on each one after every
 * trade. The engine does not know or care who is listening:
 *   - PersistenceListener saves the trade to the database
 *   - LiveFeed pushes the trade to every open browser
 * New features (e.g. email alerts) can be added without touching the engine at all.
 */
public interface TradeListener {
    void onTrade(Trade trade);
}
