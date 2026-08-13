package com.exchange.matching.monitoring;

import com.lmax.disruptor.RingBuffer;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.logging.Logger;

/**
 * Production-ready JMX MBean implementation tracking operational metrics:
 * throughput, latency, trade executions, and Disruptor ring buffer capacity.
 */
public class MatchingEngineMetrics implements MatchingEngineMetricsMBean {

    private static final Logger LOGGER = Logger.getLogger(MatchingEngineMetrics.class.getName());
    private static final MatchingEngineMetrics INSTANCE = new MatchingEngineMetrics();

    private final AtomicLong ordersProcessed = new AtomicLong(0);
    private final AtomicLong tradesExecuted = new AtomicLong(0);
    private final AtomicLong totalLatencyNanos = new AtomicLong(0);
    private final AtomicLong startTimeMs = new AtomicLong(System.currentTimeMillis());

    private RingBuffer<?> ringBuffer;

    private MatchingEngineMetrics() {
        registerMBean();
    }

    public static MatchingEngineMetrics getInstance() {
        return INSTANCE;
    }

    /**
     * Registers the MBean with the platform MBeanServer under object name "com.exchange.matching:type=MatchingEngineMetrics".
     */
    private void registerMBean() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("com.exchange.matching:type=MatchingEngineMetrics");
            if (!mbs.isRegistered(name)) {
                mbs.registerMBean(this, name);
                LOGGER.info("JMX MatchingEngineMetrics registered successfully.");
            }
        } catch (Exception e) {
            LOGGER.warning(() -> "Failed to register JMX MBean: " + e.getMessage());
        }
    }

    /**
     * Associates the RingBuffer for queue size monitoring.
     *
     * @param ringBuffer disruptor ring buffer
     */
    public void setRingBuffer(RingBuffer<?> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    /**
     * Records order processing metrics.
     *
     * @param latencyNanos elapsed nanoseconds for matching execution
     * @param tradeCount   number of trades generated
     */
    public void recordOrderProcessed(long latencyNanos, int tradeCount) {
        ordersProcessed.incrementAndGet();
        tradesExecuted.addAndGet(tradeCount);
        totalLatencyNanos.addAndGet(latencyNanos);
    }

    @Override
    public long getOrdersProcessedCount() {
        return ordersProcessed.get();
    }

    @Override
    public long getTradesExecutedCount() {
        return tradesExecuted.get();
    }

    @Override
    public double getAverageMatchingLatencyNanos() {
        long count = ordersProcessed.get();
        return count == 0 ? 0.0 : (double) totalLatencyNanos.get() / count;
    }

    @Override
    public long getRingBufferCapacity() {
        return ringBuffer != null ? ringBuffer.getBufferSize() : 0L;
    }

    @Override
    public long getRingBufferRemainingCapacity() {
        return ringBuffer != null ? ringBuffer.remainingCapacity() : 0L;
    }

    @Override
    public double getOrdersPerSecond() {
        long elapsedMs = System.currentTimeMillis() - startTimeMs.get();
        if (elapsedMs <= 0) return 0.0;
        return (ordersProcessed.get() * 1000.0) / elapsedMs;
    }

    @Override
    public void resetMetrics() {
        ordersProcessed.set(0);
        tradesExecuted.set(0);
        totalLatencyNanos.set(0);
        startTimeMs.set(System.currentTimeMillis());
    }
}
