package com.exchange.matching.benchmark;

import com.exchange.matching.domain.enums.OrderSide;
import com.exchange.matching.domain.enums.OrderType;
import com.exchange.matching.domain.model.OrderBook;
import com.exchange.matching.infrastructure.disruptor.DisruptorEngine;
import com.exchange.matching.infrastructure.pool.OrderPool;
import com.lmax.disruptor.BusySpinWaitStrategy;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark suite for measuring end-to-end throughput of the LMAX Disruptor Ring Buffer.
 * <p>
 * Evaluates performance for:
 * 1. Continuous single-producer order event ingestion
 * 2. High-density burst order event publishing
 * </p>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class RingBufferThroughputBenchmark {

    private static final String SYMBOL = "BTC-USDT";
    private DisruptorEngine disruptorEngine;
    private OrderBook orderBook;
    private OrderPool orderPool;
    private long seq;

    @Setup(Level.Trial)
    public void setupTrial() {
        orderBook = new OrderBook(SYMBOL);
        orderPool = new OrderPool(10000, 100000);
        // Initialize DisruptorEngine with a 65536 Ring Buffer size and BusySpinWaitStrategy for max throughput
        disruptorEngine = new DisruptorEngine(65536, new BusySpinWaitStrategy(), orderBook, orderPool);
        disruptorEngine.start();
        seq = 0;
    }

    @TearDown(Level.Trial)
    public void tearDownTrial() {
        if (disruptorEngine != null) {
            disruptorEngine.shutdown();
        }
    }

    /**
     * Benchmark measuring operations per second for single-producer order event publishing into the Ring Buffer.
     */
    @Benchmark
    public void benchmarkSingleProducerThroughput() {
        seq++;
        disruptorEngine.getProducer().onData(
                "ORD-" + seq,
                SYMBOL,
                (seq % 2 == 0) ? OrderSide.BUY : OrderSide.SELL,
                50000.0 + (seq % 10),
                1.0,
                System.currentTimeMillis(),
                OrderType.LIMIT
        );
    }

    /**
     * Benchmark measuring throughput during burst publishing of 100 consecutive order events.
     */
    @Benchmark
    public void benchmarkBurstOrderPublishing() {
        for (int i = 0; i < 100; i++) {
            seq++;
            disruptorEngine.getProducer().onData(
                    "BURST-" + seq,
                    SYMBOL,
                    (seq % 2 == 0) ? OrderSide.BUY : OrderSide.SELL,
                    50000.0 + (seq % 20),
                    0.5,
                    System.currentTimeMillis(),
                    OrderType.LIMIT
            );
        }
    }

    /**
     * Standalone runner method to launch JMH benchmarks from command-line or IDE.
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(RingBufferThroughputBenchmark.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
