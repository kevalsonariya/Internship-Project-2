package com.exchange.matching.domain.model;

import com.exchange.matching.domain.enums.OrderSide;
import com.exchange.matching.domain.enums.OrderStatus;
import com.exchange.matching.domain.enums.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 unit tests for {@link OrderBook} matching logic and state management.
 * Covers matching rules, priority execution, status transitions, and edge cases.
 */
class OrderBookTest {

    private OrderBook orderBook;
    private static final String SYMBOL = "BTC/USDT";

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook(SYMBOL);
    }

    @Test
    @DisplayName("Should add bid and ask orders and maintain correct top of book")
    void testAddOrdersAndTopBook() {
        Order bid1 = new Order("B1", SYMBOL, OrderSide.BUY, 49000.0, 1.0, System.currentTimeMillis(), OrderType.LIMIT);
        Order bid2 = new Order("B2", SYMBOL, OrderSide.BUY, 50000.0, 2.0, System.currentTimeMillis(), OrderType.LIMIT);

        Order ask1 = new Order("A1", SYMBOL, OrderSide.SELL, 51000.0, 1.5, System.currentTimeMillis(), OrderType.LIMIT);
        Order ask2 = new Order("A2", SYMBOL, OrderSide.SELL, 50500.0, 0.5, System.currentTimeMillis(), OrderType.LIMIT);

        assertTrue(orderBook.addOrder(bid1));
        assertTrue(orderBook.addOrder(bid2));
        assertTrue(orderBook.addOrder(ask1));
        assertTrue(orderBook.addOrder(ask2));

        assertEquals(2, orderBook.getBidOrderCount());
        assertEquals(2, orderBook.getAskOrderCount());

        PriceLevel topBid = orderBook.getBestBid();
        assertNotNull(topBid);
        assertEquals(50000.0, topBid.price());
        assertEquals(2.0, topBid.quantity());

        PriceLevel topAsk = orderBook.getBestAsk();
        assertNotNull(topAsk);
        assertEquals(50500.0, topAsk.price());
        assertEquals(0.5, topAsk.quantity());
    }

    @Test
    @DisplayName("Should cancel resting order by ID in O(1)")
    void testCancelOrder() {
        Order bid = new Order("B100", SYMBOL, OrderSide.BUY, 49500.0, 3.0, System.currentTimeMillis(), OrderType.LIMIT);
        orderBook.addOrder(bid);

        assertEquals(1, orderBook.getBidOrderCount());
        assertNotNull(orderBook.getOrder("B100"));

        Order cancelled = orderBook.cancelOrder("B100");
        assertNotNull(cancelled);
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals(0, orderBook.getBidOrderCount());
        assertNull(orderBook.getOrder("B100"));
        assertNull(orderBook.getBestBid());
    }

    @Test
    @DisplayName("Should generate MarketData depth up to max requested levels")
    void testGetDepth() {
        orderBook.addOrder(new Order("B1", SYMBOL, OrderSide.BUY, 49000.0, 1.0, System.currentTimeMillis(), OrderType.LIMIT));
        orderBook.addOrder(new Order("B2", SYMBOL, OrderSide.BUY, 48000.0, 2.0, System.currentTimeMillis(), OrderType.LIMIT));
        orderBook.addOrder(new Order("B3", SYMBOL, OrderSide.BUY, 47000.0, 3.0, System.currentTimeMillis(), OrderType.LIMIT));

        MarketData depth = orderBook.getDepth(2);
        assertNotNull(depth);
        assertEquals(SYMBOL, depth.symbol());
        assertEquals(2, depth.bids().size());
        assertEquals(49000.0, depth.bids().get(0).price());
        assertEquals(48000.0, depth.bids().get(1).price());
    }

    @Test
    @DisplayName("Should reject invalid orders (duplicate ID, invalid price/quantity, missing ID)")
    void testOrderValidations() {
        Order valid = new Order("B1", SYMBOL, OrderSide.BUY, 49000.0, 1.0, System.currentTimeMillis(), OrderType.LIMIT);
        assertTrue(orderBook.addOrder(valid));

        // Duplicate ID
        Order duplicate = new Order("B1", SYMBOL, OrderSide.BUY, 50000.0, 2.0, System.currentTimeMillis(), OrderType.LIMIT);
        assertFalse(orderBook.addOrder(duplicate));

        // Invalid price
        Order invalidPrice = new Order("B2", SYMBOL, OrderSide.BUY, 0.0, 1.0, System.currentTimeMillis(), OrderType.LIMIT);
        assertFalse(orderBook.addOrder(invalidPrice));

        // Invalid quantity
        Order invalidQty = new Order("B3", SYMBOL, OrderSide.BUY, 49000.0, -1.0, System.currentTimeMillis(), OrderType.LIMIT);
        assertFalse(orderBook.addOrder(invalidQty));

        // Empty ID
        Order emptyId = new Order("", SYMBOL, OrderSide.BUY, 49000.0, 1.0, System.currentTimeMillis(), OrderType.LIMIT);
        assertFalse(orderBook.addOrder(emptyId));
    }

    @Test
    @DisplayName("Price-Time Priority & Timestamp Ordering: multiple resting orders at the same price matched in FIFO order")
    void testPriceTimePriorityAndTimestampOrdering() {
        // Place multiple resting sell orders at the same price (50000.0) with sequential timestamps
        long baseTime = System.currentTimeMillis();
        Order ask1 = new Order("A1", SYMBOL, OrderSide.SELL, 50000.0, 1.0, baseTime, OrderType.LIMIT);
        Order ask2 = new Order("A2", SYMBOL, OrderSide.SELL, 50000.0, 2.0, baseTime + 10, OrderType.LIMIT);
        Order ask3 = new Order("A3", SYMBOL, OrderSide.SELL, 50000.0, 1.5, baseTime + 20, OrderType.LIMIT);

        assertTrue(orderBook.addOrder(ask1));
        assertTrue(orderBook.addOrder(ask2));
        assertTrue(orderBook.addOrder(ask3));

        assertEquals(3, orderBook.getAskOrderCount());

        // A taker BUY order of 2.5 units at 50000.0
        // FIFO matching should fully fill A1 (1.0), and partially fill A2 (1.5 of 2.0), leaving A3 (1.5) untouched.
        Order taker = new Order("B1", SYMBOL, OrderSide.BUY, 50000.0, 2.5, baseTime + 30, OrderType.LIMIT);
        List<Trade> trades = orderBook.match(taker);

        assertEquals(2, trades.size());
        assertEquals(OrderStatus.FILLED, taker.getStatus());

        // Verify A1 details
        Trade trade1 = trades.get(0);
        assertEquals("A1", trade1.makerOrderId());
        assertEquals("B1", trade1.takerOrderId());
        assertEquals(1.0, trade1.quantity());
        assertEquals(50000.0, trade1.price());
        assertEquals(OrderStatus.FILLED, ask1.getStatus());

        // Verify A2 details
        Trade trade2 = trades.get(1);
        assertEquals("A2", trade2.makerOrderId());
        assertEquals("B1", trade2.takerOrderId());
        assertEquals(1.5, trade2.quantity());
        assertEquals(50000.0, trade2.price());
        assertEquals(OrderStatus.PARTIALLY_FILLED, ask2.getStatus());
        assertEquals(0.5, ask2.getRemainingQuantity());

        // Verify A3 status is still NEW (no fill)
        assertEquals(OrderStatus.NEW, ask3.getStatus());
        assertEquals(1.5, ask3.getRemainingQuantity());

        // The book should now have only A2 (partially filled) and A3 resting
        assertEquals(2, orderBook.getAskOrderCount());
        assertNull(orderBook.getOrder("A1"));
        assertNotNull(orderBook.getOrder("A2"));
        assertNotNull(orderBook.getOrder("A3"));
    }

    @Test
    @DisplayName("Partial Fills: matching when quantity is less than maker's remaining quantity")
    void testPartialFills() {
        Order ask = new Order("A1", SYMBOL, OrderSide.SELL, 50000.0, 10.0, System.currentTimeMillis(), OrderType.LIMIT);
        orderBook.addOrder(ask);

        Order taker = new Order("B1", SYMBOL, OrderSide.BUY, 50000.0, 4.0, System.currentTimeMillis() + 5, OrderType.LIMIT);
        List<Trade> trades = orderBook.match(taker);

        assertEquals(1, trades.size());
        Trade trade = trades.get(0);
        assertEquals(4.0, trade.quantity());
        assertEquals(OrderStatus.FILLED, taker.getStatus());
        assertEquals(OrderStatus.PARTIALLY_FILLED, ask.getStatus());
        assertEquals(6.0, ask.getRemainingQuantity());
        assertEquals(1, orderBook.getAskOrderCount());
    }

    @Test
    @DisplayName("Full Fills: matching when quantity is equal or greater than maker's remaining quantity")
    void testFullFills() {
        Order ask = new Order("A1", SYMBOL, OrderSide.SELL, 50000.0, 5.0, System.currentTimeMillis(), OrderType.LIMIT);
        orderBook.addOrder(ask);

        Order taker = new Order("B1", SYMBOL, OrderSide.BUY, 50000.0, 5.0, System.currentTimeMillis() + 5, OrderType.LIMIT);
        List<Trade> trades = orderBook.match(taker);

        assertEquals(1, trades.size());
        Trade trade = trades.get(0);
        assertEquals(5.0, trade.quantity());
        assertEquals(OrderStatus.FILLED, taker.getStatus());
        assertEquals(OrderStatus.FILLED, ask.getStatus());
        assertEquals(0, orderBook.getAskOrderCount());
        assertNull(orderBook.getOrder("A1"));
    }

    @Test
    @DisplayName("Empty Order Book: matching against an empty book rests limit order and cancels market order")
    void testEmptyOrderBook() {
        // Limit order should rest
        Order limitTaker = new Order("B1", SYMBOL, OrderSide.BUY, 50000.0, 2.0, System.currentTimeMillis(), OrderType.LIMIT);
        List<Trade> limitTrades = orderBook.match(limitTaker);
        assertTrue(limitTrades.isEmpty());
        assertEquals(OrderStatus.NEW, limitTaker.getStatus());
        assertEquals(1, orderBook.getBidOrderCount());
        assertEquals(limitTaker, orderBook.getOrder("B1"));

        // Clear order book
        orderBook.clear();

        // Market order should immediately cancel remaining unfilled quantity
        Order marketTaker = new Order("B2", SYMBOL, OrderSide.BUY, 0.0, 2.0, System.currentTimeMillis(), OrderType.MARKET);
        List<Trade> marketTrades = orderBook.match(marketTaker);
        assertTrue(marketTrades.isEmpty());
        assertEquals(OrderStatus.CANCELLED, marketTaker.getStatus());
        assertEquals(0, orderBook.getBidOrderCount());
        assertNull(orderBook.getOrder("B2"));
    }

    @Test
    @DisplayName("Crossed Book: adding a limit order that crosses the book triggers matching")
    void testCrossedBook() {
        Order ask = new Order("A1", SYMBOL, OrderSide.SELL, 49000.0, 2.0, System.currentTimeMillis(), OrderType.LIMIT);
        orderBook.addOrder(ask);

        // Buyer is willing to pay 50000.0, which crosses with ask at 49000.0. Matching price should be maker's price (49000.0).
        Order taker = new Order("B1", SYMBOL, OrderSide.BUY, 50000.0, 2.0, System.currentTimeMillis() + 5, OrderType.LIMIT);
        List<Trade> trades = orderBook.match(taker);

        assertEquals(1, trades.size());
        Trade trade = trades.get(0);
        assertEquals(49000.0, trade.price());
        assertEquals(2.0, trade.quantity());
        assertEquals(OrderStatus.FILLED, taker.getStatus());
        assertEquals(OrderStatus.FILLED, ask.getStatus());
    }

    @Test
    @DisplayName("Market Orders: aggressive order matching against resting limits, remainder cancelled")
    void testMarketOrders() {
        Order ask1 = new Order("A1", SYMBOL, OrderSide.SELL, 50000.0, 2.0, System.currentTimeMillis(), OrderType.LIMIT);
        Order ask2 = new Order("A2", SYMBOL, OrderSide.SELL, 51000.0, 3.0, System.currentTimeMillis() + 5, OrderType.LIMIT);
        orderBook.addOrder(ask1);
        orderBook.addOrder(ask2);

        // Market BUY of 6.0 units
        // Matches A1 (2.0 @ 50000) and A2 (3.0 @ 51000)
        // Remainder (1.0) is cancelled
        Order marketBuy = new Order("B1", SYMBOL, OrderSide.BUY, 0.0, 6.0, System.currentTimeMillis() + 10, OrderType.MARKET);
        List<Trade> trades = orderBook.match(marketBuy);

        assertEquals(2, trades.size());
        assertEquals(OrderStatus.CANCELLED, marketBuy.getStatus());
        assertEquals(5.0, marketBuy.getFilledQuantity());
        assertEquals(1.0, marketBuy.getRemainingQuantity());

        assertEquals(0, orderBook.getAskOrderCount());
    }

    @Test
    @DisplayName("Multiple Orders at Same Price: aggregates depth correctly")
    void testMultipleOrdersAtSamePrice() {
        Order bid1 = new Order("B1", SYMBOL, OrderSide.BUY, 50000.0, 1.5, System.currentTimeMillis(), OrderType.LIMIT);
        Order bid2 = new Order("B2", SYMBOL, OrderSide.BUY, 50000.0, 2.5, System.currentTimeMillis() + 5, OrderType.LIMIT);

        assertTrue(orderBook.addOrder(bid1));
        assertTrue(orderBook.addOrder(bid2));

        PriceLevel bestBid = orderBook.getBestBid();
        assertNotNull(bestBid);
        assertEquals(50000.0, bestBid.price());
        assertEquals(4.0, bestBid.quantity());
        assertEquals(2, bestBid.orderCount());
    }
}
