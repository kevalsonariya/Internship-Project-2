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

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the Disruptor pipeline handlers: Risk -> Matching -> Journal.
 */
class PipelineIntegrationTest {

    private DisruptorEngine disruptorEngine;
    private IOrderBook orderBook;
    private OrderPool orderPool;
    private static final String SYMBOL = "BTC-USDT";

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook(SYMBOL);
        orderPool = new OrderPool(100, 500);
        disruptorEngine = new DisruptorEngine(256, new BusySpinWaitStrategy(), orderBook, orderPool);
        disruptorEngine.start();
    }

    @AfterEach
    void tearDown() {
        if (disruptorEngine != null) {
            disruptorEngine.shutdown();
        }
    }

    @Test
    @DisplayName("E2E Integration: Valid limit orders should flow and trigger match executions successfully")
    void testE2EPipelineSuccessfulMatching() throws InterruptedException {
        // Publish a resting sell order at 50,000
        disruptorEngine.getProducer().onData(
                "ASK1", SYMBOL, OrderSide.SELL, 50000.0, 1.5, System.currentTimeMillis(), OrderType.LIMIT
        );

        // Allow some time for processing
        TimeUnit.MILLISECONDS.sleep(100);

        // Verify the order is resting in the book
        assertNotNull(orderBook.getOrder("ASK1"));
        assertEquals(1, orderBook.getAskOrderCount());

        // Publish a matching buy order at 50,000 (quantity 1.0)
        disruptorEngine.getProducer().onData(
                "BID1", SYMBOL, OrderSide.BUY, 50000.0, 1.0, System.currentTimeMillis(), OrderType.LIMIT
        );

        TimeUnit.MILLISECONDS.sleep(100);

        // Verify bid filled completely, ask partially filled
        assertNull(orderBook.getOrder("BID1")); // Bid is fully filled, so not resting
        assertNotNull(orderBook.getOrder("ASK1")); // Ask is resting
        assertEquals(0.5, orderBook.getOrder("ASK1").getRemainingQuantity());

        // Verify PersistenceHandler persisted order states to Chronicle Map
        PersistenceHandler persistenceHandler = disruptorEngine.getPersistenceHandler();
        assertNotNull(persistenceHandler);
        assertNotNull(persistenceHandler.getOrderState("ASK1"));
        assertEquals("ASK1", persistenceHandler.getOrderState("ASK1").getOrderId());
        assertEquals(SYMBOL, persistenceHandler.getOrderState("ASK1").getSymbol());
        assertEquals(50000.0, persistenceHandler.getOrderState("ASK1").getPrice());
        assertEquals(1.5, persistenceHandler.getOrderState("ASK1").getQuantity());

        assertNotNull(persistenceHandler.getOrderState("BID1"));
        assertEquals("BID1", persistenceHandler.getOrderState("BID1").getOrderId());
    }

    @Test
    @DisplayName("E2E Integration: RiskValidationHandler should reject invalid orders and block them from matching and persistence")
    void testE2EPipelineRiskRejections() throws InterruptedException {
        // Case 1: Negative Price order
        disruptorEngine.getProducer().onData(
                "BAD-PRICE", SYMBOL, OrderSide.BUY, -100.0, 1.0, System.currentTimeMillis(), OrderType.LIMIT
        );

        // Case 2: Zero Quantity order
        disruptorEngine.getProducer().onData(
                "BAD-QTY", SYMBOL, OrderSide.BUY, 50000.0, 0.0, System.currentTimeMillis(), OrderType.LIMIT
        );

        // Case 3: Negative Quantity order
        disruptorEngine.getProducer().onData(
                "NEG-QTY", SYMBOL, OrderSide.BUY, 50000.0, -1.0, System.currentTimeMillis(), OrderType.LIMIT
        );

        TimeUnit.MILLISECONDS.sleep(100);

        // Verify none of the bad orders were added to the OrderBook
        assertNull(orderBook.getOrder("BAD-PRICE"));
        assertNull(orderBook.getOrder("BAD-QTY"));
        assertNull(orderBook.getOrder("NEG-QTY"));
        assertEquals(0, orderBook.getBidOrderCount());
        assertEquals(0, orderBook.getAskOrderCount());

        // Verify rejected orders are not persisted in PersistenceHandler
        PersistenceHandler persistenceHandler = disruptorEngine.getPersistenceHandler();
        assertNull(persistenceHandler.getOrderState("BAD-PRICE"));
        assertNull(persistenceHandler.getOrderState("BAD-QTY"));
        assertNull(persistenceHandler.getOrderState("NEG-QTY"));
    }
}
