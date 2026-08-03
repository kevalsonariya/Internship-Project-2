package com.exchange.matching.infrastructure.disruptor;

import com.lmax.disruptor.EventFactory;

/**
 * Factory class for pre-allocating {@link OrderEvent} instances in the Ring Buffer.
 */
public class OrderEventFactory implements EventFactory<OrderEvent> {

    /**
     * Instantiates a new {@link OrderEvent} structure.
     *
     * @return a new, empty order event instance
     */
    @Override
    public OrderEvent newInstance() {
        return new OrderEvent();
    }
}
