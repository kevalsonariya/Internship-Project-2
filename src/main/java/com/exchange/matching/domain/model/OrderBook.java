package com.exchange.matching.domain.model;

import com.exchange.matching.domain.enums.OrderSide;
import com.exchange.matching.domain.enums.OrderStatus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Skeleton implementation of {@link IOrderBook} maintaining Price-Time Priority for limit orders.
 * <p>
 * Bids are maintained in descending price order; Asks are maintained in ascending price order.
 * An internal index map provides O(1) order lookup and cancellation.
 * </p>
 * <p>
 * <i>Note: Matching execution mechanics will be implemented in Day 3.</i>
 * </p>
 */
public class OrderBook implements IOrderBook {

    private final String symbol;
    private final NavigableMap<Double, ArrayDeque<Order>> bids;
    private final NavigableMap<Double, ArrayDeque<Order>> asks;
    private final Map<String, Order> orderIndex;

    private double lastPrice;
    private double volume24h;

    /**
     * Constructs a new empty OrderBook for the specified trading symbol.
     *
     * @param symbol trading instrument symbol (e.g. BTC-USDT)
     */
    public OrderBook(String symbol) {
        this.symbol = Objects.requireNonNull(symbol, "symbol must not be null");
        this.bids = new TreeMap<>(Collections.reverseOrder());
        this.asks = new TreeMap<>();
        this.orderIndex = new HashMap<>();
        this.lastPrice = 0.0;
        this.volume24h = 0.0;
    }

    @Override
    public String getSymbol() {
        return symbol;
    }

    @Override
    public boolean addOrder(Order order) {
        if (order == null || !symbol.equalsIgnoreCase(order.getSymbol())) {
            return false;
        }

        NavigableMap<Double, ArrayDeque<Order>> targetMap = (order.getSide() == OrderSide.BUY) ? bids : asks;
        targetMap.computeIfAbsent(order.getPrice(), p -> new ArrayDeque<>()).offer(order);
        orderIndex.put(order.getOrderId(), order);
        return true;
    }

    @Override
    public Order cancelOrder(String orderId) {
        if (orderId == null) {
            return null;
        }

        Order order = orderIndex.remove(orderId);
        if (order == null) {
            return null;
        }

        NavigableMap<Double, ArrayDeque<Order>> targetMap = (order.getSide() == OrderSide.BUY) ? bids : asks;
        ArrayDeque<Order> queue = targetMap.get(order.getPrice());
        if (queue != null) {
            queue.remove(order);
            if (queue.isEmpty()) {
                targetMap.remove(order.getPrice());
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        return order;
    }

    @Override
    public Order getOrder(String orderId) {
        return orderIndex.get(orderId);
    }

    @Override
    public List<PriceLevel> getBids() {
        return buildPriceLevels(bids, Integer.MAX_VALUE);
    }

    @Override
    public List<PriceLevel> getAsks() {
        return buildPriceLevels(asks, Integer.MAX_VALUE);
    }

    @Override
    public PriceLevel getBestBid() {
        if (bids.isEmpty()) {
            return null;
        }
        Map.Entry<Double, ArrayDeque<Order>> entry = bids.firstEntry();
        return createPriceLevel(entry.getKey(), entry.getValue());
    }

    @Override
    public PriceLevel getBestAsk() {
        if (asks.isEmpty()) {
            return null;
        }
        Map.Entry<Double, ArrayDeque<Order>> entry = asks.firstEntry();
        return createPriceLevel(entry.getKey(), entry.getValue());
    }

    @Override
    public MarketData getDepth(int maxDepth) {
        int limit = Math.max(1, maxDepth);
        List<PriceLevel> bidLevels = buildPriceLevels(bids, limit);
        List<PriceLevel> askLevels = buildPriceLevels(asks, limit);
        long timestamp = System.currentTimeMillis();

        return new MarketData(
                symbol,
                timestamp,
                bidLevels,
                askLevels,
                lastPrice,
                volume24h
        );
    }

    @Override
    public int getBidOrderCount() {
        return countOrders(bids);
    }

    @Override
    public int getAskOrderCount() {
        return countOrders(asks);
    }

    @Override
    public void clear() {
        bids.clear();
        asks.clear();
        orderIndex.clear();
    }

    /**
     * Helper method to construct price levels up to max depth limit.
     */
    private List<PriceLevel> buildPriceLevels(NavigableMap<Double, ArrayDeque<Order>> map, int maxDepth) {
        List<PriceLevel> levels = new ArrayList<>();
        int count = 0;
        for (Map.Entry<Double, ArrayDeque<Order>> entry : map.entrySet()) {
            if (count >= maxDepth) {
                break;
            }
            levels.add(createPriceLevel(entry.getKey(), entry.getValue()));
            count++;
        }
        return levels;
    }

    /**
     * Helper method to calculate aggregate quantity and order count for a price queue.
     */
    private PriceLevel createPriceLevel(double price, ArrayDeque<Order> queue) {
        double totalQuantity = 0.0;
        int orderCount = queue.size();
        for (Order o : queue) {
            totalQuantity += o.getRemainingQuantity();
        }
        return new PriceLevel(price, totalQuantity, orderCount);
    }

    /**
     * Helper method to count total orders in a side map.
     */
    private int countOrders(NavigableMap<Double, ArrayDeque<Order>> map) {
        int total = 0;
        for (ArrayDeque<Order> q : map.values()) {
            total += q.size();
        }
        return total;
    }
}
