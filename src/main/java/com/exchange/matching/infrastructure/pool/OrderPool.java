package com.exchange.matching.infrastructure.pool;

import com.exchange.matching.domain.model.Order;

/**
 * Dedicated specialized object pool for {@link Order} domain entities.
 * <p>
 * Manages reusable, pre-allocated Order instances to eliminate GC pressure during trading.
 * </p>
 */
public class OrderPool {

    private final ObjectPool<Order> pool;

    /**
     * Constructs an OrderPool with default settings (10,000 initial, 100,000 max).
     */
    public OrderPool() {
        this(10000, 100000);
    }

    /**
     * Constructs an OrderPool with custom capacity bounds.
     *
     * @param initialCapacity initial count of pre-allocated orders
     * @param maxCapacity     maximum capacity of idle orders retained in pool
     */
    public OrderPool(int initialCapacity, int maxCapacity) {
        this.pool = new ObjectPool<>(
                initialCapacity,
                maxCapacity,
                Order::new,
                Order::reset
        );
    }

    /**
     * Borrows an idle {@link Order} instance from the pool.
     *
     * @return an idle order instance ready for initialization
     */
    public Order borrowOrder() {
        return pool.borrowObject();
    }

    /**
     * Returns an {@link Order} instance back to the pool after resetting its state.
     *
     * @param order order instance to recycle
     */
    public void returnOrder(Order order) {
        pool.returnObject(order);
    }

    /**
     * Returns the count of available idle orders currently in the pool.
     *
     * @return available idle order count
     */
    public int getAvailableCount() {
        return pool.getAvailableCount();
    }

    /**
     * Returns the maximum capacity bound of the pool.
     *
     * @return maximum pool capacity
     */
    public int getMaxCapacity() {
        return pool.getMaxCapacity();
    }

    /**
     * Clears all idle orders from the pool.
     */
    public void clear() {
        pool.clear();
    }
}
