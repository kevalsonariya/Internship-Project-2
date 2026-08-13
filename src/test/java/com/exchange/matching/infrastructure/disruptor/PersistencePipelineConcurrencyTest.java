package com.exchange.matching.infrastructure.disruptor;

import com.exchange.matching.domain.enums.OrderSide;
import com.exchange.matching.domain.enums.OrderType;
import com.exchange.matching.domain.model.IOrderBook;
import com.exchange.matching.domain.model.OrderBook;
import com.exchange.matching.infrastructure.pool.OrderPool;
import com.lmax.disruptor.BusySpinWaitStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PersistencePipelineConcurrencyTest {

    private DisruptorEngine disruptorEngine;
    private IOrderBook orderBook;
    private OrderPool orderPool;
    private SlowPersistenceHandler slowPersistenceHandler;
    private static final String SYMBOL = "BTC-USDT";

    static class SlowPersistenceHandler extends PersistenceHandler {
        final CountDownLatch pauseLatch = new CountDownLatch(1);
        final CountDownLatch eventStartedLatch = new CountDownLatch(1);
        final AtomicReference<String> persistenceThreadName = new AtomicReference<>();

        public SlowPersistenceHandler() {
            super(100L);
        }

        @Override
        public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
            persistenceThreadName.set(Thread.currentThread().getName());
            eventStartedLatch.countDown();
            try {
                // Wait for explicit test signal to unblock persistence
                pauseLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            super.onEvent(event, sequence, endOfBatch);
        }
    }

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook(SYMBOL);
        orderPool = new OrderPool(100, 500);
        slowPersistenceHandler = new SlowPersistenceHandler();
        disruptorEngine = new DisruptorEngine(
                256,
                new BusySpinWaitStrategy(),
                orderBook,
                orderPool,
                slowPersistenceHandler
        );
        disruptorEngine.start();
    }

    @AfterEach
    void tearDown() {
        if (slowPersistenceHandler != null) {
            slowPersistenceHandler.pauseLatch.countDown(); // unblock if still waiting
        }
        if (disruptorEngine != null) {
            disruptorEngine.shutdown();
        }
    }

    @Test
    @DisplayName("PersistenceHandler must run off the main matching thread and not block order matching")
    void testPersistenceDoesNotBlockMatching() throws InterruptedException {
        // Publish an order that matches in the order book
        disruptorEngine.getProducer().onData(
                "ORD-ASYNC-1", SYMBOL, OrderSide.BUY, 50000.0, 1.0, System.currentTimeMillis(), OrderType.LIMIT
        );

        // Wait until persistence handler starts processing event (and pauses on pauseLatch)
        assertTrue(slowPersistenceHandler.eventStartedLatch.await(2, TimeUnit.SECONDS), "PersistenceHandler did not start event");

        // Verify the order HAS ALREADY BEEN MATCHED in OrderBook even though PersistenceHandler is blocked!
        assertNotNull(orderBook.getOrder("ORD-ASYNC-1"), "Order should be placed in OrderBook by MatchingEngineHandler without waiting for persistence");

        // Unblock PersistenceHandler
        slowPersistenceHandler.pauseLatch.countDown();

        // Give persistence handler a moment to write to Chronicle Map
        TimeUnit.MILLISECONDS.sleep(100);

        // Verify state was ultimately persisted
        assertNotNull(slowPersistenceHandler.getOrderState("ORD-ASYNC-1"));
        assertEquals("ORD-ASYNC-1", slowPersistenceHandler.getOrderState("ORD-ASYNC-1").getOrderId());

        // Verify PersistenceHandler executed on a distinct thread from the caller and ring buffer worker
        assertNotNull(slowPersistenceHandler.persistenceThreadName.get());
        assertTrue(slowPersistenceHandler.persistenceThreadName.get().startsWith("disruptor-worker-"),
                "Persistence thread should be a separate disruptor-worker thread: " + slowPersistenceHandler.persistenceThreadName.get());
    }
}
