package com.minix.service;

import com.minix.data.HoldingRecord;
import com.minix.data.Storage;
import com.minix.data.UserRecord;
import com.minix.exceptions.AuthException;
import com.minix.model.HumanTrader;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Registration, login and "who is making this request?".
 *
 * - Passwords are never stored as plain text: we store a SHA-256 hash of them.
 * - After login the user gets a random token (UUID). The website sends this token with
 *   every request, and we look up which trader it belongs to.
 * - Each user has exactly ONE HumanTrader object in memory, shared by all their requests,
 *   so the engine always sees the same cash and reservations.
 */
public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,20}");

    private final Storage storage;
    private final Map<String, HumanTrader> sessions = new ConcurrentHashMap<>();   // token -> trader
    private final Map<String, HumanTrader> loaded = new ConcurrentHashMap<>();     // username -> trader

    public AuthService(Storage storage) {
        this.storage = storage;
    }

    /** @return a login token for the new user */
    public String register(String username, String password) throws AuthException {
        username = username == null ? "" : username.trim();
        if (!USERNAME.matcher(username).matches()) {
            throw new AuthException("Username must be 3-20 letters, digits or underscores.");
        }
        if (username.toLowerCase().startsWith("bot")) {
            throw new AuthException("Usernames starting with 'bot' are reserved.");
        }
        if (password == null || password.length() < 4) {
            throw new AuthException("Password must be at least 4 characters.");
        }
        storage.createUser(username, hash(username, password), HumanTrader.STARTING_CASH);
        return login(username, password);
    }

    /** @return a login token */
    public String login(String username, String password) throws AuthException {
        UserRecord user = storage.findUser(username == null ? "" : username.trim());
        if (user == null || !user.passwordHash().equals(hash(user.username(), password == null ? "" : password))) {
            throw new AuthException("Wrong username or password.");
        }
        HumanTrader trader = loaded.computeIfAbsent(user.username(), name -> toTrader(user));
        String token = UUID.randomUUID().toString();
        sessions.put(token, trader);
        return token;
    }

    public void logout(String token) {
        if (token != null) sessions.remove(token);
    }

    /** Finds the trader for a token, or throws if the token is missing / expired. */
    public HumanTrader authenticate(String token) throws AuthException {
        HumanTrader trader = token == null ? null : sessions.get(token);
        if (trader == null) {
            throw new AuthException("Please log in first.");
        }
        return trader;
    }

    private static HumanTrader toTrader(UserRecord user) {
        HumanTrader t = new HumanTrader(user.id(), user.username(), user.cash());
        for (HoldingRecord h : user.holdings()) {
            t.restoreHolding(h.symbol(), h.quantity(), h.avgPrice());
        }
        return t;
    }

    /** SHA-256 of "username:password", written as hexadecimal text. */
    static String hash(String username, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);   // never happens on a normal JVM
        }
    }
}
