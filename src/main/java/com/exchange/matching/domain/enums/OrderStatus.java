package com.exchange.matching.domain.enums;

/**
 * Represents the lifecycle status of an order within the exchange.
 */
public enum OrderStatus {
    /**
     * Order is active and accepted into the system.
     */
    NEW,

    /**
     * Order has been partially matched.
     */
    PARTIALLY_FILLED,

    /**
     * Order has been completely filled.
     */
    FILLED,

    /**
     * Order has been cancelled before full execution.
     */
    CANCELLED,

    /**
     * Order has been rejected due to invalid parameters or risk limits.
     */
    REJECTED
}
