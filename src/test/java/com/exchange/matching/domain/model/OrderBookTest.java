package com.exchange.matching.domain.model;

import com.exchange.matching.domain.enums.OrderSide;
import com.exchange.matching.domain.enums.OrderStatus;
import com.exchange.matching.domain.enums.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrderBook skeleton operations (adding orders, price priorities, depth calculation, cancellations).
 */
class OrderBookTest {

    private OrderBook orderBook;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook("BTC/USDT");
    }

    @Test
    @DisplayName("Should add bid and ask orders and maintain correct top of book")
    void testAddOrdersAndTopBook() {
        Order bid1 = new Order("B1", "BTC/USDT", OrderSide.BUY, 49000.0, 1.0, System.currentTimeMillis(), OrderType.LIMIT);
        Order bid2 = new Order("B2", "BTC/USDT", OrderSide.BUY, 50000.0, 2.0, System.currentTimeMillis(), OrderType.LIMIT);

        Order ask1 = new Order("A1", "BTC/USDT", OrderSide.SELL, 51000.0, 1.5, System.currentTimeMillis(), OrderType.LIMIT);
        Order ask2 = new Order("A2", "BTC/USDT", OrderSide.SELL, 50500.0, 0.5, System.currentTimeMillis(), OrderType.LIMIT);

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
        Order bid = new Order("B100", "BTC/USDT", OrderSide.BUY, 49500.0, 3.0, System.currentTimeMillis(), OrderType.LIMIT);
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
        orderBook.addOrder(new Order("B1", "BTC/USDT", OrderSide.BUY, 49000.0, 1.0, System.currentTimeMillis(), OrderType.LIMIT));
        orderBook.addOrder(new Order("B2", "BTC/USDT", OrderSide.BUY, 48000.0, 2.0, System.currentTimeMillis(), OrderType.LIMIT));
        orderBook.addOrder(new Order("B3", "BTC/USDT", OrderSide.BUY, 47000.0, 3.0, System.currentTimeMillis(), OrderType.LIMIT));

        MarketData depth = orderBook.getDepth(2);
        assertNotNull(depth);
        assertEquals("BTC/USDT", depth.symbol());
        assertEquals(2, depth.bids().size());
        assertEquals(49000.0, depth.bids().get(0).price());
        assertEquals(48000.0, depth.bids().get(1).price());
    }

    @Test
    @DisplayName("Should reject invalid orders (duplicate ID, invalid price/quantity, missing ID)")
    void testOrderValidations() {
        Order valid = new Order("B1", "BTC/USDT", OrderSide.BUY, 49000.0, 1.0, System.currentTimeMillis(), OrderType.LIMIT);
        assertTrue(orderBook.addOrder(valid));

        // Duplicate ID
        Order duplicate = new Order("B1", "BTC/USDT", OrderSide.BUY, 50000.0, 2.0, System.currentTimeMillis(), OrderType.LIMIT);
        assertFalse(orderBook.addOrder(duplicate));

        // Invalid price
        Order invalidPrice = new Order("B2", "BTC/USDT", OrderSide.BUY, 0.0, 1.0, System.currentTimeMillis(), OrderType.LIMIT);
        assertFalse(orderBook.addOrder(invalidPrice));

        // Invalid quantity
        Order invalidQty = new Order("B3", "BTC/USDT", OrderSide.BUY, 49000.0, -1.0, System.currentTimeMillis(), OrderType.LIMIT);
        assertFalse(orderBook.addOrder(invalidQty));

        // Empty ID
        Order emptyId = new Order("", "BTC/USDT", OrderSide.BUY, 49000.0, 1.0, System.currentTimeMillis(), OrderType.LIMIT);
        assertFalse(orderBook.addOrder(emptyId));
    }
}
