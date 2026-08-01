package com.exchange.matching.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Domain record representing an order book depth snapshot and market ticker metrics.
 *
 * @param symbol    trading instrument symbol
 * @param timestamp generation timestamp
 * @param bids      aggregated bid price levels (highest price first)
 * @param asks      aggregated ask price levels (lowest price first)
 * @param lastPrice price of the last executed trade
 * @param volume24h rolling 24-hour trading volume
 */
public record MarketData(
        String symbol,
        long timestamp,
        List<PriceLevel> bids,
        List<PriceLevel> asks,
        double lastPrice,
        double volume24h
) {
    /**
     * Compact constructor enforcing null safety and immutability.
     */
    public MarketData {
        Objects.requireNonNull(symbol, "symbol must not be null");
        bids = bids == null ? List.of() : List.copyOf(bids);
        asks = asks == null ? List.of() : List.copyOf(asks);
    }
}
