package com.exchange.matching.benchmark;

import com.exchange.matching.domain.enums.OrderSide;
import com.exchange.matching.domain.enums.OrderType;
import com.exchange.matching.domain.model.Order;
import com.exchange.matching.domain.model.OrderBook;
import com.exchange.matching.domain.model.Trade;
import com.exchange.matching.infrastructure.pool.OrderPool;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark suite for measuring end-to-end latency of Order Matching operations.
 * <p>
 * Simulates realistic crypto order flow (BTC-USDT) around a mid-market price spread
 * and measures latency for limit matching, resting placement, market sweeps, and mixed order flow.
 * Uses JMH {@link Blackhole} to eliminate Dead Code Elimination (DCE).
 * </p>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Thread)
public class OrderMatchingBenchmark {

    private static final String SYMBOL = "BTC-USDT";
    private static final double MID_PRICE = 50000.00;
    private static final int PREGENERATED_ORDERS_COUNT = 1000;

    private OrderBook orderBook;
    private OrderPool orderPool;
    private PreallocatedOrderData[] orderDataSequence;
    private int sequenceIndex;
    private long orderCounter;

    /**
     * Helper holder for pre-allocated order parameters to eliminate random generation overhead during benchmarking.
     */
    private record PreallocatedOrderData(
            String orderId,
            OrderSide side,
            double price,
            double quantity,
            OrderType orderType
    ) {}

    @Setup(Level.Trial)
    public void setupTrial() {
        orderPool = new OrderPool(5000, 50000);
        orderDataSequence = new PreallocatedOrderData[PREGENERATED_ORDERS_COUNT];
        Random random = new Random(42); // Fixed seed for reproducible benchmarks

        // Pre-generate a realistic stream of buy/sell orders around mid-price 50,000.00
        for (int i = 0; i < PREGENERATED_ORDERS_COUNT; i++) {
            OrderSide side = random.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
            OrderType type = (random.nextDouble() < 0.85) ? OrderType.LIMIT : OrderType.MARKET;
            
            // Generate price variance (-2.0% to +2.0% of mid-price)
            double offset = (random.nextDouble() - 0.5) * 0.04 * MID_PRICE;
            double price = Math.round((MID_PRICE + offset) * 100.0) / 100.0;
            double quantity = Math.round((0.1 + random.nextDouble() * 2.0) * 1000.0) / 1000.0;

            orderDataSequence[i] = new PreallocatedOrderData(
                    "PRE-" + i,
                    side,
                    type == OrderType.MARKET ? 0.0 : price,
                    quantity,
                    type
            );
        }
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        orderBook = new OrderBook(SYMBOL);
        sequenceIndex = 0;
        orderCounter = 0;

        // Seed initial order book with 100 resting bids and 100 resting asks around mid-price
        for (int i = 1; i <= 100; i++) {
            Order bid = orderPool.borrowOrder();
            bid.init("SEED-BID-" + i, SYMBOL, OrderSide.BUY, MID_PRICE - i * 0.5, 5.0, System.currentTimeMillis(), OrderType.LIMIT);
            orderBook.addOrder(bid);

            Order ask = orderPool.borrowOrder();
            ask.init("SEED-ASK-" + i, SYMBOL, OrderSide.SELL, MID_PRICE + i * 0.5, 5.0, System.currentTimeMillis(), OrderType.LIMIT);
            orderBook.addOrder(ask);
        }
    }

    /**
     * Benchmark measuring latency of a single crossing Limit BUY order execution against resting ASK depth.
     */
    @Benchmark
    public void benchmarkLimitOrderMatchingLatency(Blackhole bh) {
        orderCounter++;
        Order taker = orderPool.borrowOrder();
        taker.init("TAKER-LIMIT-" + orderCounter, SYMBOL, OrderSide.BUY, MID_PRICE + 1.0, 2.5, System.currentTimeMillis(), OrderType.LIMIT);
        
        List<Trade> trades = orderBook.match(taker);
        bh.consume(trades);
        bh.consume(taker);

        if (taker.isFilled()) {
            orderPool.returnOrder(taker);
        }
    }

    /**
     * Benchmark measuring latency of inserting a non-crossing Limit BUY order that rests on the order book.
     */
    @Benchmark
    public void benchmarkLimitOrderRestingLatency(Blackhole bh) {
        orderCounter++;
        Order taker = orderPool.borrowOrder();
        taker.init("TAKER-REST-" + orderCounter, SYMBOL, OrderSide.BUY, MID_PRICE - 50.0, 1.0, System.currentTimeMillis(), OrderType.LIMIT);
        
        List<Trade> trades = orderBook.match(taker);
        bh.consume(trades);
        bh.consume(taker);
    }

    /**
     * Benchmark measuring latency of executing a Market BUY order sweeping multiple ask price levels.
     */
    @Benchmark
    public void benchmarkMarketOrderSweepLatency(Blackhole bh) {
        orderCounter++;
        Order taker = orderPool.borrowOrder();
        taker.init("TAKER-MKT-" + orderCounter, SYMBOL, OrderSide.BUY, 0.0, 15.0, System.currentTimeMillis(), OrderType.MARKET);
        
        List<Trade> trades = orderBook.match(taker);
        bh.consume(trades);
        bh.consume(taker);

        orderPool.returnOrder(taker);
    }

    /**
     * Benchmark simulating a realistic, continuous mixed order flow (Limit & Market, Buy & Sell).
     */
    @Benchmark
    public void benchmarkRealisticOrderFlow(Blackhole bh) {
        PreallocatedOrderData data = orderDataSequence[sequenceIndex];
        sequenceIndex = (sequenceIndex + 1) % PREGENERATED_ORDERS_COUNT;
        orderCounter++;

        Order order = orderPool.borrowOrder();
        order.init("FLOW-" + orderCounter, SYMBOL, data.side(), data.price(), data.quantity(), System.currentTimeMillis(), data.orderType());

        List<Trade> trades = orderBook.match(order);
        bh.consume(trades);
        bh.consume(order);

        if (order.isFilled() || order.getOrderType() == OrderType.MARKET) {
            orderPool.returnOrder(order);
        }
    }

    /**
     * Standalone runner method to launch JMH latency benchmarks directly from main.
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(OrderMatchingBenchmark.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
