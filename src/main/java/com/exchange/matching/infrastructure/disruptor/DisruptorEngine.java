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

        // Setup a temporary dummy handler since LMAX Disruptor requires a handler pipeline to start.
        // Handlers will be implemented and replaced in later tasks.
        EventHandler<OrderEvent> dummyHandler = (event, sequence, endOfBatch) -> {
            // No-op for infrastructure setup stage
        };
        this.disruptor.handleEventsWith(dummyHandler);

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
