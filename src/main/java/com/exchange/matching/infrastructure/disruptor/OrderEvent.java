package com.exchange.matching.infrastructure.disruptor;

import com.exchange.matching.domain.enums.OrderSide;
import com.exchange.matching.domain.enums.OrderType;

/**
 * Represents the mutable event payload stored inside the LMAX Disruptor ring buffer.
 * <p>
 * This class is designed to be pre-allocated to avoid Garbage Collection pressure.
 * Fields are modified in-place by producers and read by consumer handlers.
 * </p>
 */
public class OrderEvent {

    private String orderId;
    private String symbol;
    private OrderSide side;
    private double price;
    private double quantity;
    private long timestamp;
    private OrderType orderType;

    /**
     * Default constructor. Pre-allocated instances start with default uninitialized state.
     */
    public OrderEvent() {
        clear();
    }

    /**
     * Clears/resets all fields to avoid keeping references to dead objects and to allow reuse.
     */
    public void clear() {
        this.orderId = null;
        this.symbol = null;
        this.side = null;
        this.price = 0.0;
        this.quantity = 0.0;
        this.timestamp = 0L;
        this.orderType = null;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    @Override
    public String toString() {
        return "OrderEvent{" +
                "orderId='" + orderId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", price=" + price +
                ", quantity=" + quantity +
                ", timestamp=" + timestamp +
                ", orderType=" + orderType +
                '}';
    }
}
