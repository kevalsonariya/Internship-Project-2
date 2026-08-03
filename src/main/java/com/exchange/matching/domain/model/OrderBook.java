package com.exchange.matching.domain.model;

import com.exchange.matching.domain.enums.OrderSide;
import com.exchange.matching.domain.enums.OrderStatus;
import com.exchange.matching.domain.enums.OrderType;
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
 * High-performance implementation of {@link IOrderBook} maintaining Price-Time (FIFO) Priority.
 * <p>
 * Bids are maintained in descending price order; Asks are maintained in ascending price order.
 * An internal index map provides O(1) order lookup and cancellation.
 * Matches incoming limit and market orders against resting order queues.
 * </p>
 */
public class OrderBook implements IOrderBook {

    /**
     * The trading instrument symbol managed by this order book (e.g. BTC-USDT).
     */
    private final String symbol;

    /**
     * Navigable map for buy orders (bids), sorted in descending order of price.
     * Each price maps to an {@link ArrayDeque} of resting orders at that price level to maintain FIFO order.
     */
    private final NavigableMap<Double, ArrayDeque<Order>> bids;

    /**
     * Navigable map for sell orders (asks), sorted in ascending order of price.
     * Each price maps to an {@link ArrayDeque} of resting orders at that price level to maintain FIFO order.
     */
    private final NavigableMap<Double, ArrayDeque<Order>> asks;

    /**
     * O(1) index map mapping unique order ID to the active resting {@link Order} in the book.
     */
    private final Map<String, Order> orderIndex;

    /**
     * Price of the last executed trade.
     */
    private double lastPrice;

    /**
     * Rolling 24-hour trading volume.
     */
    private double volume24h;

    /**
     * Sequence counter for generating unique trade identifiers.
     */
    private long tradeIdSequence;

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
        this.tradeIdSequence = 0L;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSymbol() {
        return symbol;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addOrder(Order order) {
        if (order == null || !symbol.equalsIgnoreCase(order.getSymbol())) {
            return false;
        }
        if (order.getOrderId() == null || order.getOrderId().isEmpty()) {
            return false;
        }
        if (orderIndex.containsKey(order.getOrderId())) {
            return false;
        }
        if (order.getPrice() <= 0.0 || order.getQuantity() <= 0.0) {
            return false;
        }

        NavigableMap<Double, ArrayDeque<Order>> targetMap = (order.getSide() == OrderSide.BUY) ? bids : asks;
        targetMap.computeIfAbsent(order.getPrice(), p -> new ArrayDeque<>()).offer(order);
        orderIndex.put(order.getOrderId(), order);
        return true;
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Order getOrder(String orderId) {
        return orderIndex.get(orderId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PriceLevel> getBids() {
        return buildPriceLevels(bids, Integer.MAX_VALUE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PriceLevel> getAsks() {
        return buildPriceLevels(asks, Integer.MAX_VALUE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PriceLevel getBestBid() {
        if (bids.isEmpty()) {
            return null;
        }
        Map.Entry<Double, ArrayDeque<Order>> entry = bids.firstEntry();
        return createPriceLevel(entry.getKey(), entry.getValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PriceLevel getBestAsk() {
        if (asks.isEmpty()) {
            return null;
        }
        Map.Entry<Double, ArrayDeque<Order>> entry = asks.firstEntry();
        return createPriceLevel(entry.getKey(), entry.getValue());
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public int getBidOrderCount() {
        return countOrders(bids);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getAskOrderCount() {
        return countOrders(asks);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        bids.clear();
        asks.clear();
        orderIndex.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Trade> match(Order order) {
        if (order == null || !symbol.equalsIgnoreCase(order.getSymbol())) {
            return List.of();
        }
        if (order.getOrderId() == null || order.getOrderId().isEmpty()) {
            return List.of();
        }
        if (orderIndex.containsKey(order.getOrderId())) {
            return List.of();
        }
        if (order.getPrice() <= 0.0 && order.getOrderType() == OrderType.LIMIT) {
            return List.of();
        }
        if (order.getQuantity() <= 0.0) {
            return List.of();
        }

        List<Trade> trades = null;
        NavigableMap<Double, ArrayDeque<Order>> opposingMap = (order.getSide() == OrderSide.BUY) ? asks : bids;

        while (order.getRemainingQuantity() > 0.0 && !opposingMap.isEmpty()) {
            Map.Entry<Double, ArrayDeque<Order>> bestEntry = opposingMap.firstEntry();
            double opposingPrice = bestEntry.getKey();

            // Price crossing check for LIMIT orders
            if (order.getOrderType() == OrderType.LIMIT) {
                if (order.getSide() == OrderSide.BUY && opposingPrice > order.getPrice()) {
                    break;
                }
                if (order.getSide() == OrderSide.SELL && opposingPrice < order.getPrice()) {
                    break;
                }
            }

            ArrayDeque<Order> queue = bestEntry.getValue();
            while (order.getRemainingQuantity() > 0.0 && !queue.isEmpty()) {
                Order maker = queue.peek();
                if (maker == null) {
                    queue.poll();
                    continue;
                }

                double matchQty = Math.min(order.getRemainingQuantity(), maker.getRemainingQuantity());
                if (matchQty <= 0.0) {
                    break;
                }

                order.executeFill(matchQty);
                maker.executeFill(matchQty);

                this.lastPrice = opposingPrice;
                this.volume24h += matchQty;

                String tradeId = "T-" + System.currentTimeMillis() + "-" + (++tradeIdSequence);

                String buyOrderId = (order.getSide() == OrderSide.BUY) ? order.getOrderId() : maker.getOrderId();
                String sellOrderId = (order.getSide() == OrderSide.BUY) ? maker.getOrderId() : order.getOrderId();

                Trade trade = new Trade(
                        tradeId,
                        symbol,
                        buyOrderId,
                        sellOrderId,
                        opposingPrice,
                        matchQty,
                        System.currentTimeMillis(),
                        maker.getOrderId(),
                        order.getOrderId()
                );

                if (trades == null) {
                    trades = new ArrayList<>();
                }
                trades.add(trade);

                if (maker.isFilled()) {
                    queue.poll();
                    orderIndex.remove(maker.getOrderId());
                }
            }

            if (queue.isEmpty()) {
                opposingMap.remove(opposingPrice);
            }
        }

        if (order.isFilled()) {
            // Fully filled, no need to rest
        } else {
            if (order.getOrderType() == OrderType.LIMIT) {
                NavigableMap<Double, ArrayDeque<Order>> targetMap = (order.getSide() == OrderSide.BUY) ? bids : asks;
                targetMap.computeIfAbsent(order.getPrice(), p -> new ArrayDeque<>()).offer(order);
                orderIndex.put(order.getOrderId(), order);
            } else if (order.getOrderType() == OrderType.MARKET) {
                order.setStatus(OrderStatus.CANCELLED);
            }
        }

        return trades == null ? List.of() : trades;
    }

    /**
     * Helper method to construct price levels up to max depth limit.
     *
     * @param map      the bid or ask map to build levels from
     * @param maxDepth maximum number of price levels to include
     * @return list of aggregated price levels
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
     *
     * @param price the price level
     * @param queue the queue of resting orders at this price
     * @return a {@link PriceLevel} project representation
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
     *
     * @param map the bid or ask navigable map to count orders in
     * @return total order count in the map
     */
    private int countOrders(NavigableMap<Double, ArrayDeque<Order>> map) {
        int total = 0;
        for (ArrayDeque<Order> q : map.values()) {
            total += q.size();
        }
        return total;
    }
}
