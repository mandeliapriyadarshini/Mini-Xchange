# Mini-Xchange: viva walkthrough

Read this together as a team. Every member should be able to explain all of it, not only their own part.

## 1. The idea in one paragraph

A stock exchange is a matchmaker. Buyers say the most they will pay, sellers say the least they will accept. The exchange keeps both lists sorted and, whenever a buyer's price is at least a seller's price, it makes a trade. The "share price" is simply the price of the last trade: nobody sets it.

## 2. Key terms

- **Order book:** for each stock, a list of waiting buy orders (*bids*) and waiting sell orders (*asks*).
- **Limit order:** "Buy 10 at 100 or less." Waits in the book if it cannot trade now.
- **Market order:** "Buy 10 now at the best price available." Never waits; any unfilled part is cancelled.
- **Price-time priority:** the best price trades first; between equal prices, the earliest order trades first.
- **Spread:** gap between the best ask and the best bid.
- **Reservation:** when you place a limit buy, its cost is locked so you cannot spend the same money twice.

## 3. What happens when someone clicks "Buy"

1. **Browser** (`app.js`, `submitOrder`) sends `POST /api/orders` with `symbol, side, type, quantity, price` and the login token.
2. **ApiHandler** finds the logged-in `HumanTrader` from the token (`AuthService.authenticate`), then creates a `LimitOrder` or `MarketOrder`. This is the only place that knows which subclass is used.
3. **MatchingEngine.placeOrder** (synchronized, so only one thread at a time):
   1. `order.validate()`: quantity > 0, price > 0, stock chosen. Throws `InvalidOrderException`.
   2. `checkAndReserve()`: enough cash? Throws `InsufficientFundsException`. Enough shares to sell? Throws `InsufficientSharesException`. For limit orders, cash or shares are reserved.
   3. `match()`: **the matching loop** (section 4).
   4. Leftover: a limit order is added to the `OrderBook`; a market order is cancelled.
4. For every trade, `execute()` moves cash and shares between both traders, updates the stock price, and calls every **TradeListener**:
   - `PersistenceListener` saves the trade and both humans' balances to SQLite.
   - `LiveFeed` queues the trade for the next broadcast.
5. **ApiHandler** returns an `OrderResult` as JSON ("Filled: 10 DSRT at avg 182.51"), which the page shows as a pop-up.
6. Within half a second, **LiveFeed** pushes new prices, order books and trades to *every* open browser.

If any exception is thrown in step 3, nothing has changed yet. `ApiHandler` catches it and returns `{"error": "..."}` with status 400, and the page shows the message in red.

## 4. The matching loop (the most likely viva question)

```java
while (incoming.getRemaining() > 0 && !opposite.isEmpty()) {
    Order best = opposite.peek();              // best order on the other side
    if (!incoming.crosses(best)) break;        // prices don't meet: stop
    int qty = Math.min(incoming.getRemaining(), best.getRemaining());
    double price = best.getLimitPrice();       // resting order sets the price
    trades.add(execute(incoming, best, qty, price, stock));
    if (best.getRemaining() == 0) opposite.poll();   // fully filled: remove it
}
```

**Worked example.** Sellers waiting: 5 @ 101, 5 @ 103. You send a market buy for 7.
- Loop 1: best = 5 @ 101. A market buy crosses everything. Trade 5 @ 101. That seller is now empty, so `poll()`.
- Loop 2: best = 5 @ 103. Trade 2 @ 103. The seller keeps 3.
- You still want 0, so stop. Average price = (5×101 + 2×103) / 7 = 101.57.

**Why does a market order need no special code?** `MarketOrder.getLimitPrice()` returns `Double.MAX_VALUE` for a buy and `0` for a sell, so `crosses()` is always true. That is polymorphism at work.

**Why does the resting order set the price?** It was in the book first and publicly offered that price; the newcomer accepted it. Real exchanges work the same way.

## 5. How the order book stays sorted

`OrderBook` has two `PriorityQueue<Order>` objects with different `Comparator`s:

```java
BID_ORDER = comparingDouble(Order::getLimitPrice).reversed().thenComparingLong(Order::getId);
ASK_ORDER = comparingDouble(Order::getLimitPrice).thenComparingLong(Order::getId);
```

`peek()` always returns the best order. Ties are broken by `id`. Ids come from a shared `AtomicLong` counter, so a smaller id means an earlier order, which gives time priority.

*Trap question:* "Is a PriorityQueue sorted when you loop over it?" No. Only the head is guaranteed. That's why `sortedBids()` copies it to a list and sorts it before display.

## 6. Module by module

| Package | What it does | Concepts to mention |
|---|---|---|
| `model` | Data classes: orders, traders, stocks, trades | inheritance, abstract classes, overriding, enums, encapsulation |
| `engine` | `MatchingEngine` and `OrderBook` | PriorityQueue, Comparator, synchronized, observer pattern |
| `exceptions` | Custom exceptions | checked vs unchecked, exception hierarchy |
| `data` | `Storage` interface; SQLite and in-memory versions; CSV; stock file | interfaces, JDBC, PreparedStatement, transactions, file I/O, StringTokenizer |
| `service` | Login, registration, sessions | hashing, UUID tokens, ConcurrentHashMap |
| `web` | HTTP server, API routes, live feed | HttpServer, handlers, Server-Sent Events, thread pool |
| `util` | Small JSON writer | recursion, interface |

## 7. Threads in the program

1. **Main thread:** sets everything up, then ends.
2. **Bot-Alpha and Bot-Beta:** each runs `BotTrader.run()`, a loop that sleeps 0.5–1.5 s and places a random order.
3. **HTTP worker threads:** a cached thread pool; each browser request runs on one of them.
4. **live-feed thread:** a `ScheduledExecutorService` that broadcasts every 500 ms.

They all call the engine, which is why its methods are `synchronized`. Without that, two threads could fill the same order twice or break a `PriorityQueue`.

## 8. Database design

```
users    (id PK, username UNIQUE, password_hash, cash, created_at)
holdings (user_id FK → users.id, symbol, quantity, avg_price,  PK(user_id, symbol))
trades   (id PK, symbol, price, quantity, buyer, seller, buyer_id, seller_id, time)
```

- Bots are not stored in `users`, so a bot's `buyer_id` / `seller_id` is NULL.
- `saveTraderState` uses a **transaction**: update cash, delete old holdings, insert new ones, then `commit()`, or `rollback()` if anything fails.
- `PreparedStatement` with `?` placeholders prevents SQL injection.
- Open orders are kept in memory only, so they disappear when the server restarts. Cash, holdings and trade history are kept.

`Storage` is an interface with two implementations. `Main` picks `SqliteStorage` when the driver is available, otherwise `InMemoryStorage`. The tests and the engine never know which one is in use.

## 9. Likely viva questions

- **Difference between an abstract class and an interface here?** `Order` holds shared fields and code (id, quantity, `fill()`), so it is abstract. `TradeListener` is only a contract with no shared state, so it is an interface, and unrelated classes (`LiveFeed`, `PersistenceListener`) can both implement it.
- **Why custom exceptions instead of returning false?** The message travels with the error, Java forces callers to handle it (checked), and the web layer catches all of them in one place.
- **Why is `DataAccessException` unchecked?** A broken database is not something the user can fix by changing their input.
- **What if two people buy the last shares at the same moment?** `placeOrder` is synchronized, so one finishes before the other starts. The second sees no sellers left.
- **Why reserve cash for limit orders?** Otherwise you could place ten orders that each fit your balance but together cost ten times more.
- **How do prices change?** Only through trades. `Stock.recordTrade()` sets `lastPrice` to the trade price.
- **What does the bot do?** It extends `Trader` and implements `Runnable`. Every 0.5–1.5 s it picks a random stock and places an order near the current price, slightly pulled back towards the opening price.
- **How does the browser get live updates?** It opens `/api/stream` with `EventSource`. The server keeps that connection open and writes a `data: {...}` line every 500 ms.

## 10. Future work (for the report)

- Use `BigDecimal` for money instead of `double`, to avoid rounding errors
- Stop-loss orders (a third `Order` subclass)
- Persist open orders so they survive a restart
- Smarter bots (trend-following, market-making)
- Candlestick charts and price alerts
- Deploy online with HTTPS and proper password salting (bcrypt)
