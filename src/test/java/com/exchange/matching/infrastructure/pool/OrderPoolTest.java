package com.exchange.matching.infrastructure.pool;

import com.exchange.matching.domain.enums.OrderSide;
import com.exchange.matching.domain.enums.OrderStatus;
import com.exchange.matching.domain.enums.OrderType;
import com.exchange.matching.domain.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying OrderPool functionality, pre-allocation, borrow, and recycle behavior.
 */
class OrderPoolTest {

    private OrderPool pool;

    @BeforeEach
    void setUp() {
        pool = new OrderPool(10, 50);
    }

    @Test
    @DisplayName("Should pre-allocate initial capacity on pool startup")
    void testInitialCapacity() {
        assertEquals(10, pool.getAvailableCount());
        assertEquals(50, pool.getMaxCapacity());
    }

    @Test
    @DisplayName("Should borrow and initialize an order from pool")
    void testBorrowOrder() {
        Order order = pool.borrowOrder();
        assertNotNull(order);
        assertEquals(9, pool.getAvailableCount());

        order.init("ORD-1", "BTC/USDT", OrderSide.BUY, 50000.0, 1.0, System.currentTimeMillis(), OrderType.LIMIT);
        assertEquals("ORD-1", order.getOrderId());
        assertEquals(OrderSide.BUY, order.getSide());
        assertEquals(50000.0, order.getPrice());
    }

    @Test
    @DisplayName("Should reset order state upon returning to pool")
    void testReturnOrderResetsState() {
        Order order = pool.borrowOrder();
        order.init("ORD-2", "ETH/USDT", OrderSide.SELL, 3000.0, 5.0, System.currentTimeMillis(), OrderType.LIMIT);
        order.executeFill(2.0);

        pool.returnOrder(order);
        assertEquals(10, pool.getAvailableCount());

        Order recycled = pool.borrowOrder();
        assertNull(recycled.getOrderId());
        assertNull(recycled.getSymbol());
        assertNull(recycled.getSide());
        assertEquals(0.0, recycled.getPrice());
        assertEquals(0.0, recycled.getQuantity());
        assertEquals(0.0, recycled.getFilledQuantity());
        assertEquals(OrderStatus.NEW, recycled.getStatus());
    }
}
