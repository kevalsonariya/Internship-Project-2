package com.exchange.matching.domain.model;

import java.util.Objects;

/**
 * Core domain entity/record representing an executed trade transaction.
 * <p>
 * Produced when an incoming order (taker) matches against a resting order (maker) in the order book.
 * </p>
 *
 * @param tradeId      unique trade execution identifier
 * @param symbol       trading pair symbol
 * @param buyOrderId   ID of the buy order involved in the trade
 * @param sellOrderId  ID of the sell order involved in the trade
 * @param price        executed unit price
 * @param quantity     executed trade quantity
 * @param timestamp    execution timestamp
 * @param makerOrderId ID of the maker order (resting in book)
 * @param takerOrderId ID of the taker order (aggressive incoming order)
 */
public record Trade(
        String tradeId,
        String symbol,
        String buyOrderId,
        String sellOrderId,
        double price,
        double quantity,
        long timestamp,
        String makerOrderId,
        String takerOrderId
) {
    /**
     * Compact constructor enforcing null safety.
     */
    public Trade {
        Objects.requireNonNull(tradeId, "tradeId must not be null");
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(buyOrderId, "buyOrderId must not be null");
        Objects.requireNonNull(sellOrderId, "sellOrderId must not be null");
        Objects.requireNonNull(makerOrderId, "makerOrderId must not be null");
        Objects.requireNonNull(takerOrderId, "takerOrderId must not be null");
    }
}
