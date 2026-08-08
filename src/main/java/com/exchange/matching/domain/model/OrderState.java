package com.exchange.matching.domain.model;

import com.exchange.matching.domain.enums.OrderSide;
import com.exchange.matching.domain.enums.OrderStatus;
import com.exchange.matching.domain.enums.OrderType;
import java.io.Serializable;
import java.util.Objects;

/**
 * Key-Value persistence state payload representing an order book snapshot entry.
 * <p>
 * This class captures the persistent state of an order (Order ID -> Order State)
 * suitable for off-heap persistence solutions like Chronicle Map.
 * </p>
 */
public class OrderState implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderId;
    private String symbol;
    private OrderSide side;
    private double price;
    private double quantity;
    private double filledQuantity;
    private OrderStatus status;
    private OrderType orderType;
    private long timestamp;

    /**
     * Default no-arg constructor required for serialization frameworks.
     */
    public OrderState() {
    }

    /**
     * Full-args constructor for initializing an OrderState snapshot.
     */
    public OrderState(String orderId, String symbol, OrderSide side, double price,
                      double quantity, double filledQuantity, OrderStatus status,
                      OrderType orderType, long timestamp) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.filledQuantity = filledQuantity;
        this.status = status;
        this.orderType = orderType;
        this.timestamp = timestamp;
    }

    /**
     * Creates an {@link OrderState} snapshot from a domain {@link Order} entity.
     *
     * @param order the domain order instance
     * @return a new OrderState snapshot or null if order is null
     */
    public static OrderState fromOrder(Order order) {
        if (order == null) {
            return null;
        }
        return new OrderState(
                order.getOrderId(),
                order.getSymbol(),
                order.getSide(),
                order.getPrice(),
                order.getQuantity(),
                order.getFilledQuantity(),
                order.getStatus(),
                order.getOrderType(),
                order.getTimestamp()
        );
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

    public double getFilledQuantity() {
        return filledQuantity;
    }

    public void setFilledQuantity(double filledQuantity) {
        this.filledQuantity = filledQuantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isFilled() {
        return status == OrderStatus.FILLED || filledQuantity >= quantity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderState that = (OrderState) o;
        return Double.compare(that.price, price) == 0 &&
                Double.compare(that.quantity, quantity) == 0 &&
                Double.compare(that.filledQuantity, filledQuantity) == 0 &&
                timestamp == that.timestamp &&
                Objects.equals(orderId, that.orderId) &&
                Objects.equals(symbol, that.symbol) &&
                side == that.side &&
                status == that.status &&
                orderType == that.orderType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, symbol, side, price, quantity, filledQuantity, status, orderType, timestamp);
    }

    @Override
    public String toString() {
        return "OrderState{" +
                "orderId='" + orderId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", price=" + price +
                ", quantity=" + quantity +
                ", filledQuantity=" + filledQuantity +
                ", status=" + status +
                ", orderType=" + orderType +
                ", timestamp=" + timestamp +
                '}';
    }
}
