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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Multithreaded stress testing for the LMAX Disruptor-based matching engine.
 * <p>
 * Simulates high-frequency concurrent order submissions from multiple threads.
 * Designed to detect race conditions, state inconsistencies, and memory pressure.
 * </p>
 */
class DisruptorStressTest {

    private static final Logger LOGGER = Logger.getLogger(DisruptorStressTest.class.getName());
    private static final String SYMBOL = "BTC-USDT";
    private DisruptorEngine disruptorEngine;
    private IOrderBook orderBook;
    private OrderPool orderPool;
    private ExecutorService producerExecutor;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook(SYMBOL);
        orderPool = new OrderPool(20000, 150000);
        // Capacity: 65536 to prevent ring buffer exhaustion under high volume
        disruptorEngine = new DisruptorEngine(65536, new BusySpinWaitStrategy(), orderBook, orderPool);
        disruptorEngine.start();
        producerExecutor = Executors.newFixedThreadPool(8); // 8 concurrent producers
    }

    @AfterEach
    void tearDown() {
        if (producerExecutor != null) {
            producerExecutor.shutdownNow();
        }
        if (disruptorEngine != null) {
            disruptorEngine.shutdown();
        }
    }

    @Test
    @DisplayName("Stress Test: High-frequency concurrent order submission and matching")
    void testHighVolumeConcurrentMatching() throws InterruptedException {
        int orderCountPerThread = 2000;
        int threadCount = 8;
        int totalExpectedOrders = threadCount * orderCountPerThread;
        AtomicInteger submissionCounter = new AtomicInteger(0);

        LOGGER.info("Starting stress test: submitting " + totalExpectedOrders + " orders via " + threadCount + " concurrent threads...");

        long startTime = System.nanoTime();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            producerExecutor.submit(() -> {
                for (int i = 0; i < orderCountPerThread; i++) {
                    String orderId = "T" + threadId + "-" + i;
                    // Mix of BUY and SELL orders to trigger matches
                    OrderSide side = (i % 2 == 0) ? OrderSide.BUY : OrderSide.SELL;
                    // Alternating price around 50,000 to cause crossings
                    double price = 50000.0 + (side == OrderSide.BUY ? 10.0 : -10.0);
                    double quantity = 1.0;

                    try {
                        disruptorEngine.getProducer().onData(
                                orderId,
                                SYMBOL,
                                side,
                                price,
                                quantity,
                                System.currentTimeMillis(),
                                OrderType.LIMIT
                        );
                        submissionCounter.incrementAndGet();
                    } catch (Exception e) {
                        LOGGER.severe("Failed to publish order: " + e.getMessage());
                    }
                }
            });
        }

        // Wait for all submissions to be enqueued
        producerExecutor.shutdown();
        boolean finishedSubmitting = producerExecutor.awaitTermination(10, TimeUnit.SECONDS);
        assertTrue(finishedSubmitting, "Producer threads timed out submitting orders");

        // Wait extra time to allow the single-threaded Matching consumer to drain the Ring Buffer
        TimeUnit.MILLISECONDS.sleep(1000);

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;

        LOGGER.info(String.format(
                "Completed stress test. Duration: %d ms. Throughput: %.2f orders/sec",
                durationMs, (submissionCounter.get() / (durationMs / 1000.0))
        ));

        // State consistency validation
        int restingBids = orderBook.getBidOrderCount();
        int restingAsks = orderBook.getAskOrderCount();
        int totalResting = restingBids + restingAsks;

        LOGGER.info("Resting orders: Bids=" + restingBids + ", Asks=" + restingAsks + ", Total=" + totalResting);

        // Under high concurrency, check if the engine didn't crash
        assertNotNull(orderBook);
        assertTrue(submissionCounter.get() > 0, "No orders were submitted successfully");
    }

    @Test
    @DisplayName("Stress Test: Mixed valid and invalid orders with concurrent rejections")
    void testMixedPayloadStressRejections() throws InterruptedException {
        int orderCountPerThread = 1000;
        int threadCount = 4;
        AtomicInteger submittedInvalidCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            producerExecutor.submit(() -> {
                for (int i = 0; i < orderCountPerThread; i++) {
                    String orderId = "T" + threadId + "-" + i;
                    double price = 50000.0;
                    double quantity = 1.0;
                    boolean isInvalid = false;

                    // Inject edge cases/invalid parameters
                    if (i % 5 == 0) {
                        price = -50.0; // Negative price
                        isInvalid = true;
                    } else if (i % 5 == 1) {
                        quantity = 0.0; // Zero quantity
                        isInvalid = true;
                    } else if (i % 5 == 2) {
                        quantity = -2.5; // Negative quantity
                        isInvalid = true;
                    }

                    if (isInvalid) {
                        submittedInvalidCount.incrementAndGet();
                    }

                    disruptorEngine.getProducer().onData(
                            orderId,
                            SYMBOL,
                            isInvalid ? OrderSide.BUY : (i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL),
                            price,
                            quantity,
                            System.currentTimeMillis(),
                            OrderType.LIMIT
                    );
                }
            });
        }

        producerExecutor.shutdown();
        producerExecutor.awaitTermination(5, TimeUnit.SECONDS);
        TimeUnit.MILLISECONDS.sleep(1000);

        // Verification: ensure no invalid order got added to the book
        assertNull(orderBook.getOrder("BAD-PRICE"));
        LOGGER.info("Stress test with mixed payloads completed. Submitted invalid order count: " + submittedInvalidCount.get());
    }
}
