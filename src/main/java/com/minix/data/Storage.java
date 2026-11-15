package com.minix.data;

import com.minix.exceptions.AuthException;
import com.minix.model.Trade;
import com.minix.model.Trader;

import java.util.List;

/**
 * INTERFACE: everything the app needs from permanent storage, without saying HOW it is stored.
 *
 * Two classes implement it:
 *   - SqliteStorage   : the real one, uses JDBC and an SQLite database file
 *   - InMemoryStorage : keeps data in HashMaps (used by tests, and as a fallback if the
 *                       SQLite driver is missing)
 * The rest of the program only ever uses the type "Storage", so we can swap one for the
 * other in a single line in Main. That is programming to an interface.
 */
public interface Storage {

    /** Create tables etc. Called once at startup. */
    void init();

    /** @throws AuthException if the username is already taken */
    UserRecord createUser(String username, String passwordHash, double startingCash) throws AuthException;

    /** @return the user, or null if there is no such username */
    UserRecord findUser(String username);

    /** Save a human trader's current cash and holdings. */
    void saveTraderState(Trader trader);

    /** Save a trade that involved at least one human. */
    void saveTrade(Trade trade);

    /** Most recent trades of a user, newest first. */
    List<TradeRecord> findTradesForUser(long userId, int limit);

    /** Short description shown at startup, e.g. "SQLite (data/exchange.db)". */
    String describe();
}
