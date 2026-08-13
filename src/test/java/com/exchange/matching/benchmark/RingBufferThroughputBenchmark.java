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
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark suite for measuring end-to-end throughput (ops/sec) of the LMAX Disruptor Ring Buffer.
 * <p>
 * Stress-tests the Disruptor engine under heavy concurrent/burst load with realistic market order flows.
 * Uses JMH {@link Blackhole} to eliminate Dead Code Elimination (DCE).
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
    private static final double MID_PRICE = 50000.00;
    private static final int PREGENERATED_BATCH_SIZE = 5000;

    private DisruptorEngine disruptorEngine;
    private OrderBook orderBook;
    private OrderPool orderPool;
    private PreallocatedEventData[] eventDataBatch;
    private int eventIndex;
    private long seq;

    /**
     * Helper holder for pre-allocated event parameters.
     */
    private record PreallocatedEventData(
            OrderSide side,
            double price,
            double quantity,
            OrderType orderType
    ) {}

    @Setup(Level.Trial)
    public void setupTrial() {
        orderBook = new OrderBook(SYMBOL);
        orderPool = new OrderPool(50000, 200000);

        // Initialize DisruptorEngine with a 65536 Ring Buffer size and BusySpinWaitStrategy for maximum throughput
        disruptorEngine = new DisruptorEngine(65536, new BusySpinWaitStrategy(), orderBook, orderPool);
        disruptorEngine.start();

        // Pre-allocate realistic event data to prevent random generation overhead during throughput testing
        eventDataBatch = new PreallocatedEventData[PREGENERATED_BATCH_SIZE];
        Random random = new Random(100); // Fixed seed for reproducible throughput tests

        for (int i = 0; i < PREGENERATED_BATCH_SIZE; i++) {
            OrderSide side = random.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
            OrderType type = (random.nextDouble() < 0.90) ? OrderType.LIMIT : OrderType.MARKET;
            double offset = (random.nextDouble() - 0.5) * 0.02 * MID_PRICE;
            double price = type == OrderType.MARKET ? 0.0 : Math.round((MID_PRICE + offset) * 100.0) / 100.0;
            double quantity = Math.round((0.5 + random.nextDouble() * 5.0) * 100.0) / 100.0;

            eventDataBatch[i] = new PreallocatedEventData(side, price, quantity, type);
        }

        eventIndex = 0;
        seq = 0;
    }

    @TearDown(Level.Trial)
    public void tearDownTrial() {
        if (disruptorEngine != null) {
            disruptorEngine.shutdown();
        }
    }

    /**
     * Benchmark measuring single-producer continuous order ingestion throughput (ops/sec).
     */
    @Benchmark
    public void benchmarkSingleProducerThroughput(Blackhole bh) {
        seq++;
        PreallocatedEventData data = eventDataBatch[eventIndex];
        eventIndex = (eventIndex + 1) % PREGENERATED_BATCH_SIZE;

        disruptorEngine.getProducer().onData(
                "ORD-" + seq,
                SYMBOL,
                data.side(),
                data.price(),
                data.quantity(),
                System.currentTimeMillis(),
                data.orderType()
        );
        bh.consume(seq);
    }

    /**
     * Stress benchmark measuring throughput during high-density burst publishing (500 orders per invocation).
     */
    @Benchmark
    public void benchmarkHighVolumeBurstIngestion(Blackhole bh) {
        for (int i = 0; i < 500; i++) {
            seq++;
            PreallocatedEventData data = eventDataBatch[eventIndex];
            eventIndex = (eventIndex + 1) % PREGENERATED_BATCH_SIZE;

            disruptorEngine.getProducer().onData(
                    "BURST-" + seq,
                    SYMBOL,
                    data.side(),
                    data.price(),
                    data.quantity(),
                    System.currentTimeMillis(),
                    data.orderType()
            );
        }
        bh.consume(seq);
    }

    /**
     * Standalone runner method to launch JMH throughput benchmarks directly from main.
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(RingBufferThroughputBenchmark.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
