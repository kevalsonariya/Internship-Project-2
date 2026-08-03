package com.exchange.matching.infrastructure.disruptor;

import com.exchange.matching.domain.enums.OrderSide;
import com.exchange.matching.domain.enums.OrderType;
import com.lmax.disruptor.EventTranslatorVararg;
import com.lmax.disruptor.RingBuffer;

/**
 * Producer for publishing incoming orders into the LMAX Disruptor Ring Buffer.
 * <p>
 * Uses GC-free event translators to publish data into pre-allocated event objects.
 * </p>
 */
public class OrderEventProducer {

    private final RingBuffer<OrderEvent> ringBuffer;

    /**
     * Event translator to map arguments into the target OrderEvent instance.
     */
    private static final EventTranslatorVararg<OrderEvent> TRANSLATOR = (event, sequence, args) -> {
        event.setOrderId((String) args[0]);
        event.setSymbol((String) args[1]);
        event.setSide((OrderSide) args[2]);
        event.setPrice((Double) args[3]);
        event.setQuantity((Double) args[4]);
        event.setTimestamp((Long) args[5]);
        event.setOrderType((OrderType) args[6]);
    };

    /**
     * Constructs a new producer for the specified ring buffer.
     *
     * @param ringBuffer the target disruptor ring buffer
     */
    public OrderEventProducer(RingBuffer<OrderEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    /**
     * Publishes an incoming order event to the Ring Buffer.
     *
     * @param orderId   unique order identifier
     * @param symbol    trading instrument symbol
     * @param side      BUY or SELL side
     * @param price     limit price per unit
     * @param quantity  total order quantity
     * @param timestamp submission timestamp
     * @param orderType LIMIT or MARKET execution type
     */
    public void onData(String orderId, String symbol, OrderSide side, double price, double quantity,
                       long timestamp, OrderType orderType) {
        ringBuffer.publishEvent(TRANSLATOR, orderId, symbol, side, price, quantity, timestamp, orderType);
    }
}
