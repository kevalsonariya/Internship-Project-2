package com.exchange.matching.domain.model;

import com.exchange.matching.domain.enums.OrderSide;
import com.exchange.matching.domain.enums.OrderStatus;
import com.exchange.matching.domain.enums.OrderType;
import java.util.Objects;

/**
 * Core domain entity representing an Order submitted to the matching engine.
 * <p>
 * Encapsulates order state including identifier, symbol, side, price, quantity,
 * execution type, and lifecycle status. Designed for zero-GC object pooling via {@link #reset()}.
 * </p>
 */
public class Order {

    private String orderId;
    private String symbol;
    private OrderSide side;
    private double price;
    private double quantity;
    private double filledQuantity;
    private long timestamp;
    private OrderType orderType;
    private OrderStatus status;

    /**
     * Default no-arg constructor for initialization or object pooling.
     */
    public Order() {
        this.status = OrderStatus.NEW;
    }

    /**
     * Constructs a new Order with initial values.
     *
     * @param orderId   unique order identifier
     * @param symbol    trading instrument symbol (e.g. BTC-USDT)
     * @param side      BUY or SELL side
     * @param price     limit price per unit
     * @param quantity  total order quantity
     * @param timestamp submission timestamp
     * @param orderType LIMIT or MARKET execution type
     */
    public Order(String orderId, String symbol, OrderSide side, double price, double quantity,
                 long timestamp, OrderType orderType) {
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        this.symbol = Objects.requireNonNull(symbol, "symbol must not be null");
        this.side = Objects.requireNonNull(side, "side must not be null");
        this.price = price;
        this.quantity = quantity;
        this.filledQuantity = 0.0;
        this.timestamp = timestamp;
        this.orderType = Objects.requireNonNull(orderType, "orderType must not be null");
        this.status = OrderStatus.NEW;
    }

    /**
     * Constructs an Order with all explicit fields.
     *
     * @param orderId        unique order identifier
     * @param symbol         trading instrument symbol
     * @param side           BUY or SELL side
     * @param price          limit price per unit
     * @param quantity       total order quantity
     * @param filledQuantity quantity executed so far
     * @param timestamp      submission timestamp
     * @param orderType      LIMIT or MARKET execution type
     * @param status         current order lifecycle status
     */
    public Order(String orderId, String symbol, OrderSide side, double price, double quantity,
                 double filledQuantity, long timestamp, OrderType orderType, OrderStatus status) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.filledQuantity = filledQuantity;
        this.timestamp = timestamp;
        this.orderType = orderType;
        this.status = status;
    }

    /**
     * Re-initializes a pooled order instance with new parameters.
     *
     * @param orderId   unique order identifier
     * @param symbol    trading instrument symbol
     * @param side      BUY or SELL side
     * @param price     limit price per unit
     * @param quantity  total order quantity
     * @param timestamp submission timestamp
     * @param orderType LIMIT or MARKET execution type
     */
    public void init(String orderId, String symbol, OrderSide side, double price, double quantity,
                     long timestamp, OrderType orderType) {
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        this.symbol = Objects.requireNonNull(symbol, "symbol must not be null");
        this.side = Objects.requireNonNull(side, "side must not be null");
        this.price = price;
        this.quantity = quantity;
        this.filledQuantity = 0.0;
        this.timestamp = timestamp;
        this.orderType = Objects.requireNonNull(orderType, "orderType must not be null");
        this.status = OrderStatus.NEW;
    }

    /**
     * Resets all fields of this order to default values for object pool reuse.
     */
    public void reset() {
        this.orderId = null;
        this.symbol = null;
        this.side = null;
        this.price = 0.0;
        this.quantity = 0.0;
        this.filledQuantity = 0.0;
        this.timestamp = 0L;
        this.orderType = null;
        this.status = OrderStatus.NEW;
    }

    /**
     * Gets the unique order identifier.
     *
     * @return order ID string
     */
    public String getOrderId() {
        return orderId;
    }

    /**
     * Sets the unique order identifier.
     *
     * @param orderId order ID string
     */
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    /**
     * Gets the trading pair symbol.
     *
     * @return trading symbol
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Sets the trading pair symbol.
     *
     * @param symbol trading symbol
     */
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Gets the side of the order (BUY/SELL).
     *
     * @return order side
     */
    public OrderSide getSide() {
        return side;
    }

    /**
     * Sets the side of the order.
     *
     * @param side order side
     */
    public void setSide(OrderSide side) {
        this.side = side;
    }

    /**
     * Gets the order unit price.
     *
     * @return price
     */
    public double getPrice() {
        return price;
    }

    /**
     * Sets the order unit price.
     *
     * @param price price
     */
    public void setPrice(double price) {
        this.price = price;
    }

    /**
     * Gets the total requested quantity.
     *
     * @return order quantity
     */
    public double getQuantity() {
        return quantity;
    }

    /**
     * Sets the total requested quantity.
     *
     * @param quantity total quantity
     */
    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    /**
     * Gets the executed filled quantity.
     *
     * @return filled quantity
     */
    public double getFilledQuantity() {
        return filledQuantity;
    }

    /**
     * Sets the executed filled quantity.
     *
     * @param filledQuantity filled quantity
     */
    public void setFilledQuantity(double filledQuantity) {
        this.filledQuantity = filledQuantity;
    }

    /**
     * Calculates the remaining unfilled quantity.
     *
     * @return remaining quantity (quantity - filledQuantity)
     */
    public double getRemainingQuantity() {
        return Math.max(0.0, quantity - filledQuantity);
    }

    /**
     * Gets the timestamp when order was placed.
     *
     * @return timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp when order was placed.
     *
     * @param timestamp timestamp
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Gets the order execution type (LIMIT/MARKET).
     *
     * @return order execution type
     */
    public OrderType getOrderType() {
        return orderType;
    }

    /**
     * Sets the order execution type.
     *
     * @param orderType order execution type
     */
    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    /**
     * Gets the current order status.
     *
     * @return order status
     */
    public OrderStatus getStatus() {
        return status;
    }

    /**
     * Sets the current order status.
     *
     * @param status order status
     */
    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    /**
     * Fills a portion of the order and automatically updates filled quantity and status.
     *
     * @param fillQuantity quantity to fill
     */
    public void executeFill(double fillQuantity) {
        this.filledQuantity += fillQuantity;
        if (this.filledQuantity >= this.quantity) {
            this.status = OrderStatus.FILLED;
        } else {
            this.status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    /**
     * Indicates whether the order is fully filled.
     *
     * @return true if filledQuantity >= quantity
     */
    public boolean isFilled() {
        return this.filledQuantity >= this.quantity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(orderId, order.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", price=" + price +
                ", quantity=" + quantity +
                ", filledQuantity=" + filledQuantity +
                ", timestamp=" + timestamp +
                ", orderType=" + orderType +
                ", status=" + status +
                '}';
    }
}
