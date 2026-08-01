package com.exchange.matching.domain.model;

/**
 * Domain record representing liquidity depth at a specific price point.
 *
 * @param price      price level
 * @param quantity   aggregate order volume available at this price level
 * @param orderCount count of active orders resting at this price level
 */
public record PriceLevel(
        double price,
        double quantity,
        int orderCount
) {
}
