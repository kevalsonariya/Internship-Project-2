package com.exchange.matching.domain.enums;

/**
 * Represents the side of an order in the matching engine.
 * <p>
 * An order can either be a {@link #BUY} (bid) or a {@link #SELL} (ask).
 * </p>
 */
public enum OrderSide {
    /**
     * Buy order (Bid).
     */
    BUY,

    /**
     * Sell order (Ask).
     */
    SELL;

    /**
     * Returns the opposite order side.
     *
     * @return {@link #SELL} if this side is {@link #BUY}, otherwise {@link #BUY}.
     */
    public OrderSide opposite() {
        return this == BUY ? SELL : BUY;
    }
}
