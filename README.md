# Mini-Xchange

A mini stock exchange simulator (paper trading) built in Java for the Object Oriented Programming course project.

Users register, receive ₹1,00,000 of virtual cash, and trade four fictional stocks through a website. A **matching engine** written in Java pairs buyers with sellers using price-time priority, **bot traders** running on background threads keep prices moving, and every trade is saved to an **SQLite database** through JDBC.

## Features

- Register / log in (passwords stored as SHA-256 hashes)
- Live market dashboard: prices, price chart, order book, recent trades, all updating twice a second
- Limit and market orders, partial fills, order cancellation
- Portfolio with average buy price and live profit / loss
- Trade history with **CSV export**
- Clear error messages for insufficient funds, insufficient shares and invalid orders

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Web server | Java's built-in `com.sun.net.httpserver` (no framework) |
| Live updates | Server-Sent Events |
| Database | SQLite via JDBC (`sqlite-jdbc` driver) |
| Front end | HTML, CSS, JavaScript, Chart.js |
| Build | Maven |
| Tests | JUnit 4 |

## How to run

**Requirements:** JDK 17 or newer, and Maven (IntelliJ IDEA includes Maven).

```bash
mvn package            # compiles, runs the tests, builds target/mini-xchange.jar
java -jar target/mini-xchange.jar
```

Then open **http://localhost:7070** and create an account.

**In IntelliJ:** File → Open → select the `Mini-Xchange` folder (it is detected as a Maven project). Then open `src/main/java/com/minix/Main.java` and click the green Run button.

Run only the tests with `mvn test`.

To use a different port: `PORT=8080 java -jar target/mini-xchange.jar`

### Demo tip
Open the site in two browser windows (or on two laptops on the same Wi-Fi, using the host laptop's IP address), log in as two different users, and trade between them: the second window updates live.

## Project structure

```
src/main/java/com/minix/
├── Main.java            starts and connects everything
├── model/               Order, LimitOrder, MarketOrder, Trader, HumanTrader, BotTrader,
│                        Stock, Trade, PricePoint, Side, OrderStatus, Tradable, Validatable
├── engine/              MatchingEngine, OrderBook, OrderResult, TradeListener
├── exceptions/          ExchangeException and its subclasses, DataAccessException
├── data/                Storage (interface), SqliteStorage, InMemoryStorage,
│                        PersistenceListener, CsvExporter, StockLoader, records
├── service/             AuthService (register, login, sessions)
├── web/                 WebServer, ApiHandler, LiveFeed, StaticFileHandler, HttpUtil
└── util/                Json, JsonSerializable
src/main/resources/public/   index.html, app.html, css/, js/
src/test/java/               MatchingEngineTest (14 tests)
data/stocks.csv              listed companies (edit to add your own)
docs/WALKTHROUGH.md          plain-English explanation for the viva
```

## OOP concepts used

| Concept | Where |
|---|---|
| Classes, objects, encapsulation | every model class: private fields, public getters |
| Inheritance | `Order` → `LimitOrder`, `MarketOrder`; `Trader` → `HumanTrader`, `BotTrader`; exception hierarchy |
| Abstract classes | `Order`, `Trader` |
| Polymorphism / overriding | `getLimitPrice()`, `canRestInBook()`, `validate()`, `isBot()` |
| Interfaces | `TradeListener`, `Tradable`, `Validatable`, `Storage`, `JsonSerializable` |
| Custom exceptions | `InsufficientFundsException`, `InsufficientSharesException`, `InvalidOrderException`, `AuthException`, `DataAccessException` |
| Collections | `PriorityQueue`, `HashMap`, `LinkedHashMap`, `ArrayList`, `LinkedList`, `ArrayDeque` |
| Multithreading | `BotTrader implements Runnable`, `synchronized` engine, `ScheduledExecutorService` live feed |
| File handling | `StockLoader` (read with `BufferedReader` + `StringTokenizer`), `CsvExporter` (write) |
| Database connectivity | `SqliteStorage`: JDBC, `PreparedStatement`, transactions |
| Enums, records, lambdas, generics | `Side`, `UserRecord`, comparators, `Map<String, Integer>` |

## Screenshots

See `docs/screenshots/`.
