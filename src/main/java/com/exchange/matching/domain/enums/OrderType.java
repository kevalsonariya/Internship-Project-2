package com.exchange.matching.domain.enums;

/**
 * Represents the execution type of an order in the matching engine.
 */
public enum OrderType {
    /**
     * Limit order: Executes at the specified price or better.
     */
    LIMIT,

    /**
     * Market order: Executes immediately at the best available market price.
     */
    MARKET
}
