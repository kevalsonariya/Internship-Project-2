package com.exchange.matching.infrastructure.disruptor;

import com.exchange.matching.domain.enums.OrderStatus;
import com.exchange.matching.domain.model.OrderState;
import com.lmax.disruptor.EventHandler;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Event handler responsible for persisting order book state to off-heap key-value storage (Chronicle Map).
 * <p>
 * Implements {@link EventHandler} for LMAX Disruptor integration, mapping Order IDs to {@link OrderState}.
 * This handler is currently built as an unwired skeleton as part of the Day 3 persistence milestone.
 * </p>
 */
public class PersistenceHandler implements EventHandler<OrderEvent>, AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(PersistenceHandler.class.getName());

    private final ChronicleMap<String, OrderState> orderStateMap;
    private final boolean managesMapLifecycle;

    /**
     * Default constructor creating an in-memory off-heap Chronicle Map store.
     */
    public PersistenceHandler() {
        this(100_000L);
    }

    /**
     * Constructs a PersistenceHandler with specified capacity in-memory.
     *
     * @param maxEntries maximum expected entries in the Chronicle Map
     */
    public PersistenceHandler(long maxEntries) {
        OrderState sampleState = new OrderState(
                "ORD-10000000", "BTC-USDT", com.exchange.matching.domain.enums.OrderSide.BUY,
                50000.00, 10.0000, 0.0000, OrderStatus.NEW,
                com.exchange.matching.domain.enums.OrderType.LIMIT, System.currentTimeMillis()
        );

        this.orderStateMap = ChronicleMapBuilder
                .of(String.class, OrderState.class)
                .name("order-book-state-map")
                .entries(maxEntries)
                .averageKey("ORD-10000000")
                .averageValue(sampleState)
                .create();
        this.managesMapLifecycle = true;
    }

    /**
     * Constructs a PersistenceHandler backed by a file on disk.
     *
     * @param file       file destination for Chronicle Map persistence
     * @param maxEntries maximum expected entries
     * @throws IOException if file creation or memory mapping fails
     */
    public PersistenceHandler(File file, long maxEntries) throws IOException {
        OrderState sampleState = new OrderState(
                "ORD-10000000", "BTC-USDT", com.exchange.matching.domain.enums.OrderSide.BUY,
                50000.00, 10.0000, 0.0000, OrderStatus.NEW,
                com.exchange.matching.domain.enums.OrderType.LIMIT, System.currentTimeMillis()
        );

        this.orderStateMap = ChronicleMapBuilder
                .of(String.class, OrderState.class)
                .name("order-book-state-map")
                .entries(maxEntries)
                .averageKey("ORD-10000000")
                .averageValue(sampleState)
                .createPersistedTo(file);
        this.managesMapLifecycle = true;
    }

    /**
     * Constructs a PersistenceHandler using an existing {@link ChronicleMap} instance.
     *
     * @param orderStateMap the ChronicleMap instance to populate
     */
    public PersistenceHandler(ChronicleMap<String, OrderState> orderStateMap) {
        this.orderStateMap = orderStateMap;
        this.managesMapLifecycle = false;
    }

    /**
     * Processes an incoming {@link OrderEvent} and updates the persistent key-value state store.
     *
     * @param event      the order event from the ring buffer
     * @param sequence   the sequence number of the event
     * @param endOfBatch indicates if this is the end of the batch
     */
    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        if (event == null || event.isRejected() || event.getOrderId() == null) {
            return;
        }

        OrderStatus status = event.getQuantity() > 0 ? OrderStatus.NEW : OrderStatus.REJECTED;

        OrderState state = new OrderState(
                event.getOrderId(),
                event.getSymbol(),
                event.getSide(),
                event.getPrice(),
                event.getQuantity(),
                0.0,
                status,
                event.getOrderType(),
                event.getTimestamp()
        );

        orderStateMap.put(event.getOrderId(), state);

        LOGGER.fine(() -> String.format(
                "Persisted OrderState [Sequence: %d]: Order ID: %s, Symbol: %s, Side: %s, Qty: %.4f @ %.2f",
                sequence, state.getOrderId(), state.getSymbol(), state.getSide(), state.getQuantity(), state.getPrice()
        ));
    }

    /**
     * Retrieves the stored state for a given order ID.
     *
     * @param orderId the order ID
     * @return the corresponding {@link OrderState} snapshot or null if not found
     */
    public OrderState getOrderState(String orderId) {
        return orderStateMap.get(orderId);
    }

    /**
     * Returns the underlying ChronicleMap instance.
     *
     * @return the {@link ChronicleMap}
     */
    public ChronicleMap<String, OrderState> getOrderStateMap() {
        return orderStateMap;
    }

    /**
     * Returns the total count of persisted orders in the map.
     *
     * @return total size
     */
    public int size() {
        return orderStateMap.size();
    }

    @Override
    public void close() {
        if (managesMapLifecycle && orderStateMap != null && !orderStateMap.isClosed()) {
            orderStateMap.close();
        }
    }
}
