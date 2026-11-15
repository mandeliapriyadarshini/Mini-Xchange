package com.minix.web;

import com.minix.data.CsvExporter;
import com.minix.data.Storage;
import com.minix.engine.MatchingEngine;
import com.minix.engine.OrderResult;
import com.minix.exceptions.AuthException;
import com.minix.exceptions.ExchangeException;
import com.minix.exceptions.InvalidOrderException;
import com.minix.model.HumanTrader;
import com.minix.model.LimitOrder;
import com.minix.model.MarketOrder;
import com.minix.model.Order;
import com.minix.model.Side;
import com.minix.model.Stock;
import com.minix.service.AuthService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The REST API used by the website's JavaScript. Every URL starting with /api/ arrives here.
 *
 *   POST /api/register      username, password        -> token
 *   POST /api/login         username, password        -> token
 *   POST /api/logout
 *   GET  /api/stocks                                  -> all stocks with prices
 *   GET  /api/orderbook     ?symbol=DSRT              -> waiting buyers and sellers
 *   GET  /api/history       ?symbol=DSRT              -> price points for the chart
 *   GET  /api/portfolio                               -> my cash, holdings, profit/loss
 *   GET  /api/orders                                  -> my open orders
 *   POST /api/orders        symbol, side, type, quantity, price
 *   POST /api/orders/cancel id
 *   GET  /api/mytrades                                -> my trade history
 *   GET  /api/export        ?token=...                -> download my trades as CSV
 *
 * Logged-in requests send their token in the "X-Auth-Token" header.
 * Errors come back as {"error": "..."} with a suitable HTTP status code.
 */
public class ApiHandler implements HttpHandler {

    private final MatchingEngine engine;
    private final AuthService auth;
    private final Storage storage;
    private final CsvExporter csvExporter;

    public ApiHandler(MatchingEngine engine, AuthService auth, Storage storage, CsvExporter csvExporter) {
        this.engine = engine;
        this.auth = auth;
        this.storage = storage;
        this.csvExporter = csvExporter;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String route = ex.getRequestMethod() + " " + ex.getRequestURI().getPath();
        try {
            Map<String, String> p = HttpUtil.readParams(ex);
            switch (route) {
                case "POST /api/register" -> tokenResponse(ex, auth.register(p.get("username"), p.get("password")));
                case "POST /api/login" -> tokenResponse(ex, auth.login(p.get("username"), p.get("password")));
                case "POST /api/logout" -> {
                    auth.logout(token(ex, p));
                    HttpUtil.sendJson(ex, 200, Map.of("ok", true));
                }
                case "GET /api/stocks" -> HttpUtil.sendJson(ex, 200, engine.getStocks());
                case "GET /api/orderbook" -> HttpUtil.sendJson(ex, 200,
                        engine.getOrderBookSnapshot(HttpUtil.require(p, "symbol"), 10));
                case "GET /api/history" -> HttpUtil.sendJson(ex, 200, history(HttpUtil.require(p, "symbol")));
                case "GET /api/portfolio" -> HttpUtil.sendJson(ex, 200, portfolio(user(ex, p)));
                case "GET /api/orders" -> HttpUtil.sendJson(ex, 200, engine.getOpenOrders(user(ex, p)));
                case "POST /api/orders" -> HttpUtil.sendJson(ex, 200, placeOrder(user(ex, p), p));
                case "POST /api/orders/cancel" -> {
                    Order cancelled = engine.cancelOrder(HttpUtil.requireInt(p, "id"), user(ex, p));
                    HttpUtil.sendJson(ex, 200, Map.of("message", "Cancelled order #" + cancelled.getId(),
                            "order", cancelled));
                }
                case "GET /api/mytrades" -> HttpUtil.sendJson(ex, 200,
                        storage.findTradesForUser(user(ex, p).getId(), 200));
                case "GET /api/export" -> exportCsv(ex, user(ex, p));
                default -> HttpUtil.sendJson(ex, 404, Map.of("error", "No such API route: " + route));
            }
        } catch (AuthException e) {
            HttpUtil.sendJson(ex, 401, Map.of("error", e.getMessage()));
        } catch (ExchangeException | HttpUtil.BadRequestException e) {
            HttpUtil.sendJson(ex, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            HttpUtil.sendJson(ex, 500, Map.of("error", "Server error: " + e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String token(HttpExchange ex, Map<String, String> p) {
        String header = ex.getRequestHeaders().getFirst("X-Auth-Token");
        return header != null ? header : p.get("token");   // ?token= is used for the CSV download link
    }

    private HumanTrader user(HttpExchange ex, Map<String, String> p) throws AuthException {
        return auth.authenticate(token(ex, p));
    }

    private void tokenResponse(HttpExchange ex, String token) throws IOException, AuthException {
        HumanTrader t = auth.authenticate(token);
        HttpUtil.sendJson(ex, 200, Map.of("token", token, "username", t.getUsername()));
    }

    /** Builds an Order object from the form fields. This is where the subclass is chosen. */
    private OrderResult placeOrder(HumanTrader trader, Map<String, String> p)
            throws ExchangeException, HttpUtil.BadRequestException {
        String symbol = HttpUtil.require(p, "symbol");
        Side side;
        try {
            side = Side.valueOf(HttpUtil.require(p, "side").toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidOrderException("Side must be BUY or SELL.");
        }
        int quantity = HttpUtil.requireInt(p, "quantity");
        String type = p.getOrDefault("type", "LIMIT").toUpperCase();

        Order order = switch (type) {
            case "LIMIT" -> new LimitOrder(trader, symbol, side, quantity, HttpUtil.requireDouble(p, "price"));
            case "MARKET" -> new MarketOrder(trader, symbol, side, quantity);
            default -> throw new InvalidOrderException("Order type must be LIMIT or MARKET.");
        };
        return engine.placeOrder(order);   // may throw InsufficientFunds / InsufficientShares / InvalidOrder
    }

    private Map<String, Object> history(String symbol) throws InvalidOrderException {
        synchronized (engine) {
            Stock s = engine.getStock(symbol);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("symbol", s.getSymbol());
            m.put("points", s.getHistory());
            return m;
        }
    }

    /**
     * Cash, every holding valued at the current market price, and profit / loss.
     * The engine is locked while reading so a bot trade cannot change numbers halfway.
     */
    private Map<String, Object> portfolio(HumanTrader t) throws InvalidOrderException {
        synchronized (engine) {
            List<Map<String, Object>> rows = new ArrayList<>();
            double holdingsValue = 0;
            for (Map.Entry<String, Integer> h : t.getHoldings().entrySet()) {
                String symbol = h.getKey();
                int qty = h.getValue();
                double last = engine.getStock(symbol).getLastPrice();
                double avg = t.getAveragePrice(symbol);
                double value = qty * last;
                double pnl = (last - avg) * qty;
                holdingsValue += value;

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("symbol", symbol);
                row.put("quantity", qty);
                row.put("reserved", t.getReservedShares(symbol));
                row.put("avgPrice", avg);
                row.put("lastPrice", last);
                row.put("value", round2(value));
                row.put("pnl", round2(pnl));
                row.put("pnlPct", avg == 0 ? 0 : round2((last - avg) / avg * 100));
                rows.add(row);
            }
            double total = t.getCash() + holdingsValue;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", t.getUsername());
            m.put("cash", t.getCash());
            m.put("reservedCash", t.getReservedCash());
            m.put("availableCash", t.getAvailableCash());
            m.put("holdingsValue", round2(holdingsValue));
            m.put("totalValue", round2(total));
            m.put("totalPnl", round2(total - HumanTrader.STARTING_CASH));
            m.put("totalPnlPct", round2((total - HumanTrader.STARTING_CASH) / HumanTrader.STARTING_CASH * 100));
            m.put("holdings", rows);
            return m;
        }
    }

    private void exportCsv(HttpExchange ex, HumanTrader t) throws IOException {
        Path file = csvExporter.export(t.getUsername(), storage.findTradesForUser(t.getId(), 10_000));
        ex.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"" + file.getFileName() + "\"");
        HttpUtil.send(ex, 200, "text/csv; charset=utf-8", Files.readAllBytes(file));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
