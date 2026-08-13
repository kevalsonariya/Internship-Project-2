package com.exchange.matching.monitoring;

/**
 * Standard JMX MBean interface exposing key operational metrics for the matching engine.
 */
public interface MatchingEngineMetricsMBean {

    /**
     * Gets the total number of orders processed by the matching engine.
     *
     * @return orders processed count
     */
    long getOrdersProcessedCount();

    /**
     * Gets the total number of trades executed by the matching engine.
     *
     * @return trades executed count
     */
    long getTradesExecutedCount();

    /**
     * Gets the average matching execution latency in nanoseconds.
     *
     * @return average matching latency (ns)
     */
    double getAverageMatchingLatencyNanos();

    /**
     * Gets the maximum capacity of the Disruptor ring buffer.
     *
     * @return ring buffer total capacity
     */
    long getRingBufferCapacity();

    /**
     * Gets the current remaining/available capacity in the Disruptor ring buffer.
     *
     * @return ring buffer remaining capacity
     */
    long getRingBufferRemainingCapacity();

    /**
     * Gets the calculated throughput in orders processed per second.
     *
     * @return orders per second throughput
     */
    double getOrdersPerSecond();

    /**
     * Resets accumulated counters and latency tracking.
     */
    void resetMetrics();
}
