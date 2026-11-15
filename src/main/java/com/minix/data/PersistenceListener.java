package com.minix.data;

import com.minix.engine.TradeListener;
import com.minix.exceptions.DataAccessException;
import com.minix.model.Trade;

/**
 * Implements TradeListener: whenever the engine reports a trade, save it.
 * Trades between two bots are not saved (they would fill the database with noise);
 * as soon as a human is involved, the trade and their new balances are written.
 */
public class PersistenceListener implements TradeListener {

    private final Storage storage;

    public PersistenceListener(Storage storage) {
        this.storage = storage;
    }

    @Override
    public void onTrade(Trade trade) {
        boolean buyerHuman = !trade.getBuyer().isBot();
        boolean sellerHuman = !trade.getSeller().isBot();
        if (!buyerHuman && !sellerHuman) return;

        try {
            storage.saveTrade(trade);
            if (buyerHuman) storage.saveTraderState(trade.getBuyer());
            if (sellerHuman && trade.getSeller() != trade.getBuyer()) storage.saveTraderState(trade.getSeller());
        } catch (DataAccessException e) {
            // never let a database problem crash the matching engine; log it instead
            System.err.println("Warning: could not save trade " + trade + ": " + e.getMessage());
        }
    }
}
