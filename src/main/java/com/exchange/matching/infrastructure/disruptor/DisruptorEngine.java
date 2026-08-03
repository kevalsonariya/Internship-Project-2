package com.exchange.matching.infrastructure.disruptor;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import java.util.concurrent.ThreadFactory;

/**
 * Manages the lifecycle and configuration of the LMAX Disruptor Ring Buffer.
 * <p>
 * Configures the ring buffer with low-latency wait strategies and prepares the producer
 * for publishing events. Single-producer configuration is chosen by default to optimize
 * throughput for the order matching engine thread confinement design.
 * </p>
 */
public class DisruptorEngine {

    private final Disruptor<OrderEvent> disruptor;
    private final OrderEventProducer producer;

    /**
     * Constructs a DisruptorEngine with default configuration (1024 buffer size, BusySpinWaitStrategy, Single Producer).
     */
    public DisruptorEngine() {
        this(1024, new BusySpinWaitStrategy());
    }

    /**
     * Constructs a DisruptorEngine with custom capacity and wait strategy.
     *
     * @param bufferSize   must be a power of 2
     * @param waitStrategy wait strategy for consumers waiting on the ring buffer
     */
    public DisruptorEngine(int bufferSize, WaitStrategy waitStrategy) {
        this(bufferSize, waitStrategy, new com.exchange.matching.domain.model.OrderBook("DEFAULT"), new com.exchange.matching.infrastructure.pool.OrderPool());
    }

    /**
     * Constructs a DisruptorEngine with custom capacity, wait strategy, order book, and order pool.
     *
     * @param bufferSize   must be a power of 2
     * @param waitStrategy wait strategy for consumers waiting on the ring buffer
     * @param orderBook    the order book to match against
     * @param orderPool    the order pool for borrowing/returning orders
     */
    public DisruptorEngine(int bufferSize, WaitStrategy waitStrategy, 
                           com.exchange.matching.domain.model.IOrderBook orderBook, 
                           com.exchange.matching.infrastructure.pool.OrderPool orderPool) {
        if (Integer.bitCount(bufferSize) != 1) {
            throw new IllegalArgumentException("bufferSize must be a power of 2");
        }

        ThreadFactory threadFactory = r -> {
            Thread thread = new Thread(r);
            thread.setName("disruptor-worker");
            thread.setDaemon(true);
            return thread;
        };

        this.disruptor = new Disruptor<>(
                new OrderEventFactory(),
                bufferSize,
                threadFactory,
                ProducerType.SINGLE,
                waitStrategy
        );

        // Instantiates pipeline event handlers
        RiskValidationHandler riskHandler = new RiskValidationHandler();
        MatchingEngineHandler matchingHandler = new MatchingEngineHandler(orderBook, orderPool);
        JournalingHandler journalHandler = new JournalingHandler();

        // Wire handlers sequentially: Risk -> Matching -> Journal
        this.disruptor.handleEventsWith(riskHandler)
                      .then(matchingHandler)
                      .then(journalHandler);

        this.producer = new OrderEventProducer(disruptor.getRingBuffer());
    }

    /**
     * Starts the Disruptor processing thread pool.
     */
    public void start() {
        disruptor.start();
    }

    /**
     * Stops the Disruptor and waits until all published events are processed.
     */
    public void shutdown() {
        disruptor.shutdown();
    }

    /**
     * Gets the event producer associated with this ring buffer.
     *
     * @return the {@link OrderEventProducer}
     */
    public OrderEventProducer getProducer() {
        return producer;
    }

    /**
     * Gets the underlying RingBuffer instance.
     *
     * @return the {@link RingBuffer} of {@link OrderEvent}s
     */
    public RingBuffer<OrderEvent> getRingBuffer() {
        return disruptor.getRingBuffer();
    }

    /**
     * Gets the underlying Disruptor instance.
     *
     * @return the {@link Disruptor}
     */
    public Disruptor<OrderEvent> getDisruptor() {
        return disruptor;
    }
}
