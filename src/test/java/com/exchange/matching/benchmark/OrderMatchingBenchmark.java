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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark suite for measuring latency of Order Matching operations.
 * <p>
 * Evaluates execution performance for:
 * 1. Limit order matching against resting depth
 * 2. Non-crossing limit order resting insertion
 * 3. Market order execution sweeping liquidity
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
    private OrderBook orderBook;
    private OrderPool orderPool;
    private long orderCounter;

    @Setup(Level.Trial)
    public void setupTrial() {
        orderPool = new OrderPool(1000, 10000);
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        orderBook = new OrderBook(SYMBOL);
        orderCounter = 0;

        // Populate order book with resting liquidity depth
        // Bids: 99.0 down to 90.0
        // Asks: 101.0 up to 110.0
        for (int i = 1; i <= 50; i++) {
            Order bid = orderPool.borrowOrder();
            bid.init("BID-" + i, SYMBOL, OrderSide.BUY, 100.0 - i * 0.5, 10.0, System.currentTimeMillis(), OrderType.LIMIT);
            orderBook.addOrder(bid);

            Order ask = orderPool.borrowOrder();
            ask.init("ASK-" + i, SYMBOL, OrderSide.SELL, 100.0 + i * 0.5, 10.0, System.currentTimeMillis(), OrderType.LIMIT);
            orderBook.addOrder(ask);
        }
    }

    /**
     * Benchmark measuring latency of matching a Limit BUY order against resting ASK liquidity.
     */
    @Benchmark
    public List<Trade> benchmarkLimitOrderMatching() {
        orderCounter++;
        Order taker = orderPool.borrowOrder();
        taker.init("TAKER-LIMIT-" + orderCounter, SYMBOL, OrderSide.BUY, 101.50, 5.0, System.currentTimeMillis(), OrderType.LIMIT);
        List<Trade> trades = orderBook.match(taker);
        if (taker.isFilled()) {
            orderPool.returnOrder(taker);
        }
        return trades;
    }

    /**
     * Benchmark measuring latency of placing a non-crossing Limit BUY order that rests on the order book.
     */
    @Benchmark
    public List<Trade> benchmarkLimitOrderResting() {
        orderCounter++;
        Order taker = orderPool.borrowOrder();
        taker.init("TAKER-REST-" + orderCounter, SYMBOL, OrderSide.BUY, 95.00, 1.0, System.currentTimeMillis(), OrderType.LIMIT);
        return orderBook.match(taker);
    }

    /**
     * Benchmark measuring latency of executing a Market BUY order that sweeps multiple ask levels.
     */
    @Benchmark
    public List<Trade> benchmarkMarketOrderMatching() {
        orderCounter++;
        Order taker = orderPool.borrowOrder();
        taker.init("TAKER-MKT-" + orderCounter, SYMBOL, OrderSide.BUY, 0.0, 25.0, System.currentTimeMillis(), OrderType.MARKET);
        List<Trade> trades = orderBook.match(taker);
        orderPool.returnOrder(taker);
        return trades;
    }

    /**
     * Standalone runner method to launch JMH benchmarks from command-line or IDE.
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(OrderMatchingBenchmark.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
