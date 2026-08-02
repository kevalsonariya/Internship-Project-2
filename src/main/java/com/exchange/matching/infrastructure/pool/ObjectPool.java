package com.exchange.matching.infrastructure.pool;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * High-performance, low-GC object pool for reusable domain objects.
 * <p>
 * Pre-allocates object instances during initialization and manages borrowing
 * and recycling to eliminate Garbage Collection runtime overhead during high-frequency operations.
 * </p>
 *
 * @param <T> the type of object managed by this pool
 */
public class ObjectPool<T> {

    private final Queue<T> pool;
    private final Supplier<T> factory;
    private final Consumer<T> resetter;
    private final int maxCapacity;

    /**
     * Constructs a new ObjectPool with specified capacities and object lifecycle hooks.
     *
     * @param initialCapacity initial number of objects to pre-allocate
     * @param maxCapacity     maximum capacity of objects to hold in pool
     * @param factory         supplier creating new object instances
     * @param resetter        consumer resetting object state upon release back to pool
     */
    public ObjectPool(int initialCapacity, int maxCapacity, Supplier<T> factory, Consumer<T> resetter) {
        if (initialCapacity < 0 || maxCapacity <= 0 || initialCapacity > maxCapacity) {
            throw new IllegalArgumentException("Invalid pool bounds: initial=" 
                    + initialCapacity + ", max=" + maxCapacity);
        }
        this.factory = factory;
        this.resetter = resetter;
        this.maxCapacity = maxCapacity;
        this.pool = new ArrayDeque<>(maxCapacity);

        for (int i = 0; i < initialCapacity; i++) {
            pool.offer(factory.get());
        }
    }

    /**
     * Borrows an instance from the pool.
     * <p>
     * Returns a pooled instance if available; otherwise allocates a new instance via factory supplier.
     * </p>
     *
     * @return an object instance of type {@code T}
     */
    public T borrowObject() {
        T instance = pool.poll();
        if (instance == null) {
            instance = factory.get();
        }
        return instance;
    }

    /**
     * Returns an instance back to the pool for reuse.
     * <p>
     * Resets the state of the object using the resetter consumer before recycling.
     * </p>
     *
     * @param instance object instance to release back to pool
     */
    public void returnObject(T instance) {
        if (instance == null) {
            return;
        }
        if (resetter != null) {
            resetter.accept(instance);
        }
        if (pool.size() < maxCapacity) {
            pool.offer(instance);
        }
    }

    /**
     * Returns the number of currently available objects in the pool.
     *
     * @return count of available idle objects
     */
    public int getAvailableCount() {
        return pool.size();
    }

    /**
     * Returns the maximum capacity of the pool.
     *
     * @return maximum pool capacity
     */
    public int getMaxCapacity() {
        return maxCapacity;
    }

    /**
     * Clears all pooled object instances from the pool queue.
     */
    public void clear() {
        pool.clear();
    }
}
