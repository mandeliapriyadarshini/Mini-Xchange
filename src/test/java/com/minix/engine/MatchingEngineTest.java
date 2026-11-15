package com.minix.engine;

import com.minix.exceptions.InsufficientFundsException;
import com.minix.exceptions.InsufficientSharesException;
import com.minix.exceptions.InvalidOrderException;
import com.minix.model.HumanTrader;
import com.minix.model.LimitOrder;
import com.minix.model.MarketOrder;
import com.minix.model.OrderStatus;
import com.minix.model.Side;
import com.minix.model.Stock;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Proves the matching engine follows the rules. Run with: mvn test
 * Each test sets up a fresh engine with one stock (DSRT at 100) and two traders.
 */
public class MatchingEngineTest {

    private MatchingEngine engine;
    private HumanTrader alice;   // buyer, starts with 100,000 cash
    private HumanTrader bob;     // seller, starts with 100,000 cash and 500 DSRT

    @Before
    public void setUp() {
        engine = new MatchingEngine();
        engine.addStock(new Stock("DSRT", "Desert Solar", 100.0));
        alice = new HumanTrader(1, "alice", 100_000);
        bob = new HumanTrader(2, "bob", 100_000);
        bob.restoreHolding("DSRT", 500, 90.0);
    }

    @Test
    public void limitOrdersThatCrossTradeAtTheRestingPrice() throws Exception {
        engine.placeOrder(new LimitOrder(bob, "DSRT", Side.SELL, 10, 101));
        OrderResult r = engine.placeOrder(new LimitOrder(alice, "DSRT", Side.BUY, 10, 102));

        assertEquals(1, r.getTrades().size());
        assertEquals(101.0, r.getTrades().get(0).getPrice(), 0.001);   // seller was there first
        assertEquals(100_000 - 1010, alice.getCash(), 0.001);
        assertEquals(10, alice.getShares("DSRT"));
        assertEquals(0, alice.getReservedCash(), 0.001);               // reservation fully released
        assertEquals(101.0, engine.getStock("DSRT").getLastPrice(), 0.001);
    }

    @Test
    public void ordersThatDoNotCrossWaitInTheBook() throws Exception {
        engine.placeOrder(new LimitOrder(bob, "DSRT", Side.SELL, 10, 105));
        OrderResult r = engine.placeOrder(new LimitOrder(alice, "DSRT", Side.BUY, 10, 100));

        assertTrue(r.getTrades().isEmpty());
        assertEquals(OrderStatus.OPEN, r.getOrder().getStatus());
        assertEquals(1000, alice.getReservedCash(), 0.001);
        assertEquals(2, engine.getOpenOrders(alice).size() + engine.getOpenOrders(bob).size());
    }

    @Test
    public void samePriceIsFilledInTimeOrder() throws Exception {
        HumanTrader carol = new HumanTrader(3, "carol", 100_000);
        carol.restoreHolding("DSRT", 100, 90.0);
        engine.placeOrder(new LimitOrder(bob, "DSRT", Side.SELL, 5, 101));    // first at 101
        engine.placeOrder(new LimitOrder(carol, "DSRT", Side.SELL, 5, 101));  // second at 101

        OrderResult r = engine.placeOrder(new LimitOrder(alice, "DSRT", Side.BUY, 5, 101));

        assertEquals("bob", r.getTrades().get(0).getSeller().getUsername());
    }

    @Test
    public void betterPriceIsFilledFirst() throws Exception {
        engine.placeOrder(new LimitOrder(bob, "DSRT", Side.SELL, 5, 103));
        engine.placeOrder(new LimitOrder(bob, "DSRT", Side.SELL, 5, 101));

        OrderResult r = engine.placeOrder(new MarketOrder(alice, "DSRT", Side.BUY, 7));

        assertEquals(2, r.getTrades().size());
        assertEquals(101.0, r.getTrades().get(0).getPrice(), 0.001);
        assertEquals(103.0, r.getTrades().get(1).getPrice(), 0.001);
        assertEquals(101.57, r.getAveragePrice(), 0.001);   // (5*101 + 2*103) / 7 = 711 / 7
    }

    @Test
    public void marketOrderWithTooFewSellersIsPartlyFilledAndRestCancelled() throws Exception {
        engine.placeOrder(new LimitOrder(bob, "DSRT", Side.SELL, 6, 101));

        OrderResult r = engine.placeOrder(new MarketOrder(alice, "DSRT", Side.BUY, 10));

        assertEquals(6, r.getFilledQuantity());
        assertEquals(OrderStatus.CANCELLED, r.getOrder().getStatus());
        assertTrue(engine.getOpenOrders(alice).isEmpty());
    }

    @Test
    public void partlyFilledLimitOrderKeepsTheRestInTheBook() throws Exception {
        engine.placeOrder(new LimitOrder(bob, "DSRT", Side.SELL, 4, 100));

        OrderResult r = engine.placeOrder(new LimitOrder(alice, "DSRT", Side.BUY, 10, 100));

        assertEquals(4, r.getFilledQuantity());
        assertEquals(OrderStatus.PARTIALLY_FILLED, r.getOrder().getStatus());
        assertEquals(600, alice.getReservedCash(), 0.001);   // 6 shares still waiting at 100
    }

    @Test(expected = InsufficientFundsException.class)
    public void cannotBuyWithoutEnoughCash() throws Exception {
        engine.placeOrder(new LimitOrder(alice, "DSRT", Side.BUY, 2000, 100));   // needs 200,000
    }

    @Test(expected = InsufficientFundsException.class)
    public void reservedCashCannotBeSpentTwice() throws Exception {
        engine.placeOrder(new LimitOrder(alice, "DSRT", Side.BUY, 900, 100));    // reserves 90,000
        engine.placeOrder(new LimitOrder(alice, "DSRT", Side.BUY, 200, 100));    // needs 20,000 more
    }

    @Test(expected = InsufficientSharesException.class)
    public void cannotSellSharesYouDoNotOwn() throws Exception {
        engine.placeOrder(new LimitOrder(alice, "DSRT", Side.SELL, 1, 100));
    }

    @Test(expected = InvalidOrderException.class)
    public void zeroQuantityIsRejected() throws Exception {
        engine.placeOrder(new LimitOrder(alice, "DSRT", Side.BUY, 0, 100));
    }

    @Test(expected = InvalidOrderException.class)
    public void unknownStockIsRejected() throws Exception {
        engine.placeOrder(new LimitOrder(alice, "NOPE", Side.BUY, 1, 100));
    }

    @Test
    public void cancellingGivesTheReservationBack() throws Exception {
        OrderResult r = engine.placeOrder(new LimitOrder(alice, "DSRT", Side.BUY, 10, 99));
        assertEquals(990, alice.getReservedCash(), 0.001);

        engine.cancelOrder(r.getOrder().getId(), alice);

        assertEquals(0, alice.getReservedCash(), 0.001);
        assertEquals(OrderStatus.CANCELLED, r.getOrder().getStatus());
    }

    @Test(expected = InvalidOrderException.class)
    public void cannotCancelSomeoneElsesOrder() throws Exception {
        OrderResult r = engine.placeOrder(new LimitOrder(alice, "DSRT", Side.BUY, 10, 99));
        engine.cancelOrder(r.getOrder().getId(), bob);
    }

    @Test
    public void listenersAreToldAboutEveryTrade() throws Exception {
        int[] count = {0};
        engine.addListener(trade -> count[0]++);   // a lambda implementing TradeListener
        engine.placeOrder(new LimitOrder(bob, "DSRT", Side.SELL, 5, 100));
        engine.placeOrder(new LimitOrder(bob, "DSRT", Side.SELL, 5, 101));
        engine.placeOrder(new MarketOrder(alice, "DSRT", Side.BUY, 10));
        assertEquals(2, count[0]);
    }
}
