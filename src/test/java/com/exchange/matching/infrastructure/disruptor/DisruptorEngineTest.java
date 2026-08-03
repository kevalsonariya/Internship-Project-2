package com.exchange.matching.infrastructure.disruptor;

import com.exchange.matching.domain.enums.OrderSide;
import com.exchange.matching.domain.enums.OrderType;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.EventHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests to verify the initialization, publishing, and lifecycle of the LMAX Disruptor setup.
 */
class DisruptorEngineTest {

    private DisruptorEngine disruptorEngine;

    @BeforeEach
    void setUp() {
        disruptorEngine = new DisruptorEngine(1024, new BusySpinWaitStrategy());
    }

    @AfterEach
    void tearDown() {
        if (disruptorEngine != null) {
            disruptorEngine.shutdown();
        }
    }

    @Test
    @DisplayName("Should initialize Disruptor correctly with specified buffer size")
    void testDisruptorInitialization() {
        assertNotNull(disruptorEngine.getDisruptor());
        assertNotNull(disruptorEngine.getRingBuffer());
        assertNotNull(disruptorEngine.getProducer());
        assertEquals(1024, disruptorEngine.getRingBuffer().getBufferSize());
    }

    @Test
    @DisplayName("Should throw exception if buffer size is not a power of 2")
    void testInvalidBufferSize() {
        assertThrows(IllegalArgumentException.class, () -> new DisruptorEngine(1023, new BusySpinWaitStrategy()));
    }

    @Test
    @DisplayName("Should successfully publish and process an order event through the Ring Buffer")
    void testEventPublishAndConsumption() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<OrderEvent> capturedEvent = new AtomicReference<>();

        EventHandler<OrderEvent> testHandler = (event, sequence, endOfBatch) -> {
            OrderEvent copy = new OrderEvent();
            copy.setOrderId(event.getOrderId());
            copy.setSymbol(event.getSymbol());
            copy.setSide(event.getSide());
            copy.setPrice(event.getPrice());
            copy.setQuantity(event.getQuantity());
            copy.setTimestamp(event.getTimestamp());
            copy.setOrderType(event.getOrderType());
            capturedEvent.set(copy);
            latch.countDown();
        };

        // Override/add test handler to the disruptor pipeline
        disruptorEngine.getDisruptor().handleEventsWith(testHandler);
        disruptorEngine.start();

        long timestamp = System.currentTimeMillis();
        disruptorEngine.getProducer().onData(
                "O123",
                "BTC-USDT",
                OrderSide.BUY,
                55000.50,
                2.5,
                timestamp,
                OrderType.LIMIT
        );

        // Wait for handler to process the event
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Event processing timed out");

        OrderEvent result = capturedEvent.get();
        assertNotNull(result);
        assertEquals("O123", result.getOrderId());
        assertEquals("BTC-USDT", result.getSymbol());
        assertEquals(OrderSide.BUY, result.getSide());
        assertEquals(55000.50, result.getPrice());
        assertEquals(2.5, result.getQuantity());
        assertEquals(timestamp, result.getTimestamp());
        assertEquals(OrderType.LIMIT, result.getOrderType());
    }
}
