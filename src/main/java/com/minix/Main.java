package com.minix;

import com.minix.data.CsvExporter;
import com.minix.data.InMemoryStorage;
import com.minix.data.PersistenceListener;
import com.minix.data.SqliteStorage;
import com.minix.data.StockLoader;
import com.minix.data.Storage;
import com.minix.engine.MatchingEngine;
import com.minix.model.BotTrader;
import com.minix.model.Stock;
import com.minix.service.AuthService;
import com.minix.web.ApiHandler;
import com.minix.web.LiveFeed;
import com.minix.web.StaticFileHandler;
import com.minix.web.WebServer;

import java.nio.file.Path;
import java.util.List;

/**
 * Starts Mini-Xchange. Wires every module together:
 *
 *   storage  ->  engine  ->  listeners (database, live feed)  ->  bots  ->  web server
 *
 * Run it, then open http://localhost:7070 in a browser.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));
        Path dataDir = Path.of("data");

        // 1. Storage: the real SQLite database, or memory if the driver jar is missing
        Storage storage = SqliteStorage.isDriverAvailable()
                ? new SqliteStorage(dataDir.resolve("exchange.db"))
                : new InMemoryStorage();
        storage.init();

        // 2. The exchange itself, with the companies read from data/stocks.csv
        MatchingEngine engine = new MatchingEngine();
        List<Stock> stocks = StockLoader.load(dataDir.resolve("stocks.csv"));
        for (Stock s : stocks) engine.addStock(s);

        // 3. Listeners: save trades, and push them to browsers
        engine.addListener(new PersistenceListener(storage));
        LiveFeed liveFeed = new LiveFeed(engine);
        engine.addListener(liveFeed);
        liveFeed.start();

        // 4. Bots: fill the order books, then trade on their own threads
        BotTrader[] bots = {
                new BotTrader(-1, "Bot-Alpha", engine, 11),
                new BotTrader(-2, "Bot-Beta", engine, 22)
        };
        for (BotTrader bot : bots) {
            bot.seedLiquidity();
            Thread t = new Thread(bot, bot.getUsername());
            t.setDaemon(true);   // daemon: the thread stops automatically when the program ends
            t.start();
        }

        // 5. Website
        AuthService auth = new AuthService(storage);
        ApiHandler api = new ApiHandler(engine, auth, storage, new CsvExporter(dataDir.resolve("exports")));
        WebServer server = new WebServer(port, api, liveFeed, new StaticFileHandler());
        server.start();

        System.out.println("==============================================");
        System.out.println("  Mini-Xchange is running");
        System.out.println("  Open  http://localhost:" + port + "  in your browser");
        System.out.println("  Storage: " + storage.describe());
        System.out.println("  Stocks : " + stocks.size() + " listed, " + bots.length + " bots trading");
        System.out.println("  Press Ctrl+C to stop");
        System.out.println("==============================================");
    }
}
