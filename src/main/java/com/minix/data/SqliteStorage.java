package com.minix.data;

import com.minix.exceptions.AuthException;
import com.minix.exceptions.DataAccessException;
import com.minix.model.Trade;
import com.minix.model.Trader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DATABASE CONNECTIVITY (JDBC): stores users, holdings and trades in an SQLite file.
 *
 * Key JDBC ideas used here:
 *   - DriverManager.getConnection("jdbc:sqlite:...")  opens the database
 *   - PreparedStatement with ? placeholders            safe from SQL injection
 *   - ResultSet                                        reading rows of a SELECT
 *   - try-with-resources                               statements are always closed
 *   - transactions (setAutoCommit(false) / commit)     all-or-nothing updates
 *
 * One connection is shared, and every method is synchronized, because SQLite allows only
 * one writer at a time and several threads (website + bots) may call us.
 */
public class SqliteStorage implements Storage {

    private final Path dbFile;
    private Connection connection;

    public SqliteStorage(Path dbFile) {
        this.dbFile = dbFile;
    }

    /** True if the SQLite JDBC driver jar is on the classpath. */
    public static boolean isDriverAvailable() {
        try {
            Class.forName("org.sqlite.JDBC");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public synchronized void init() {
        try {
            if (dbFile.getParent() != null) Files.createDirectories(dbFile.getParent());
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS users (
                            id            INTEGER PRIMARY KEY AUTOINCREMENT,
                            username      TEXT    NOT NULL UNIQUE,
                            password_hash TEXT    NOT NULL,
                            cash          REAL    NOT NULL,
                            created_at    TEXT    DEFAULT CURRENT_TIMESTAMP
                        )""");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS holdings (
                            user_id   INTEGER NOT NULL REFERENCES users(id),
                            symbol    TEXT    NOT NULL,
                            quantity  INTEGER NOT NULL,
                            avg_price REAL    NOT NULL,
                            PRIMARY KEY (user_id, symbol)
                        )""");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS trades (
                            id        INTEGER PRIMARY KEY AUTOINCREMENT,
                            symbol    TEXT    NOT NULL,
                            price     REAL    NOT NULL,
                            quantity  INTEGER NOT NULL,
                            buyer     TEXT    NOT NULL,
                            seller    TEXT    NOT NULL,
                            buyer_id  INTEGER,
                            seller_id INTEGER,
                            time      INTEGER NOT NULL
                        )""");
            }
        } catch (Exception e) {
            throw new DataAccessException("Could not open database " + dbFile, e);
        }
    }

    @Override
    public synchronized UserRecord createUser(String username, String passwordHash, double startingCash)
            throws AuthException {
        if (findUser(username) != null) {
            throw new AuthException("Username '" + username + "' is already taken.");
        }
        String sql = "INSERT INTO users (username, password_hash, cash) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setDouble(3, startingCash);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new UserRecord(keys.getLong(1), username, passwordHash, startingCash, List.of());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not create user", e);
        }
    }

    @Override
    public synchronized UserRecord findUser(String username) {
        String sql = "SELECT id, username, password_hash, cash FROM users WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                long id = rs.getLong("id");
                return new UserRecord(id, rs.getString("username"), rs.getString("password_hash"),
                        rs.getDouble("cash"), loadHoldings(id));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read user", e);
        }
    }

    private List<HoldingRecord> loadHoldings(long userId) throws SQLException {
        List<HoldingRecord> list = new ArrayList<>();
        String sql = "SELECT symbol, quantity, avg_price FROM holdings WHERE user_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new HoldingRecord(rs.getString(1), rs.getInt(2), rs.getDouble(3)));
                }
            }
        }
        return list;
    }

    /**
     * Saves cash and replaces all holdings of the user inside ONE TRANSACTION:
     * either every statement succeeds, or none of them is applied (rollback).
     */
    @Override
    public synchronized void saveTraderState(Trader trader) {
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement cash = connection.prepareStatement("UPDATE users SET cash = ? WHERE id = ?");
                 PreparedStatement del = connection.prepareStatement("DELETE FROM holdings WHERE user_id = ?");
                 PreparedStatement ins = connection.prepareStatement(
                         "INSERT INTO holdings (user_id, symbol, quantity, avg_price) VALUES (?, ?, ?, ?)")) {
                cash.setDouble(1, trader.getCash());
                cash.setLong(2, trader.getId());
                cash.executeUpdate();

                del.setLong(1, trader.getId());
                del.executeUpdate();

                for (Map.Entry<String, Integer> h : trader.getHoldings().entrySet()) {
                    ins.setLong(1, trader.getId());
                    ins.setString(2, h.getKey());
                    ins.setInt(3, h.getValue());
                    ins.setDouble(4, trader.getAveragePrice(h.getKey()));
                    ins.addBatch();
                }
                ins.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save trader " + trader.getUsername(), e);
        }
    }

    @Override
    public synchronized void saveTrade(Trade t) {
        String sql = "INSERT INTO trades (symbol, price, quantity, buyer, seller, buyer_id, seller_id, time) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, t.getSymbol());
            ps.setDouble(2, t.getPrice());
            ps.setInt(3, t.getQuantity());
            ps.setString(4, t.getBuyer().getUsername());
            ps.setString(5, t.getSeller().getUsername());
            setIdOrNull(ps, 6, t.getBuyer());
            setIdOrNull(ps, 7, t.getSeller());
            ps.setLong(8, t.getTime());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not save trade", e);
        }
    }

    /** Bots are not stored in the users table, so their id column is left empty (NULL). */
    private static void setIdOrNull(PreparedStatement ps, int index, Trader trader) throws SQLException {
        if (trader.isBot()) ps.setNull(index, java.sql.Types.INTEGER);
        else ps.setLong(index, trader.getId());
    }

    @Override
    public synchronized List<TradeRecord> findTradesForUser(long userId, int limit) {
        String sql = "SELECT time, symbol, price, quantity, buyer, seller, buyer_id FROM trades "
                   + "WHERE buyer_id = ? OR seller_id = ? ORDER BY id DESC LIMIT ?";
        List<TradeRecord> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean iBought = rs.getLong("buyer_id") == userId && !rs.wasNull();
                    list.add(new TradeRecord(
                            rs.getLong("time"),
                            rs.getString("symbol"),
                            iBought ? "BUY" : "SELL",
                            rs.getInt("quantity"),
                            rs.getDouble("price"),
                            iBought ? rs.getString("seller") : rs.getString("buyer")));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read trades", e);
        }
        return list;
    }

    @Override
    public String describe() {
        return "SQLite database (" + dbFile + ")";
    }
}
