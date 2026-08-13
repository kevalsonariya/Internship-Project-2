package com.exchange.matching.infrastructure.disruptor;

import com.exchange.matching.domain.model.IOrderBook;
import com.exchange.matching.domain.model.Order;
import com.exchange.matching.domain.model.Trade;
import com.exchange.matching.infrastructure.pool.OrderPool;
import com.lmax.disruptor.EventHandler;
import java.util.List;
import java.util.logging.Logger;

/**
 * Event handler responsible for executing the core matching logic on the {@link IOrderBook}.
 * <p>
 * If the event is not rejected, it borrows a domain {@link Order} from the {@link OrderPool},
 * initializes it, and executes matching. Gc-free resource recycling is performed for filled
 * or immediate-execution orders.
 * </p>
 */
public class MatchingEngineHandler implements EventHandler<OrderEvent> {

    private static final Logger LOGGER = Logger.getLogger(MatchingEngineHandler.class.getName());

    private final IOrderBook orderBook;
    private final OrderPool orderPool;

    /**
     * Constructs a new MatchingEngineHandler.
     *
     * @param orderBook the order book to process matches against
     * @param orderPool the pool to borrow and return domain order instances
     */
    public MatchingEngineHandler(IOrderBook orderBook, OrderPool orderPool) {
        this.orderBook = orderBook;
        this.orderPool = orderPool;
    }

    /**
     * Translates the event to a domain Order and submits it to the matching engine.
     *
     * @param event      the order event from the ring buffer
     * @param sequence   the sequence number
     * @param endOfBatch indicates if this is the end of the batch
     */
    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        if (event == null || event.isRejected()) {
            return;
        }

        // Borrow a domain Order from pool
        Order order = orderPool.borrowOrder();
        order.init(
                event.getOrderId(),
                event.getSymbol(),
                event.getSide(),
                event.getPrice(),
                event.getQuantity(),
                event.getTimestamp(),
                event.getOrderType()
        );

        long startTimeNanos = System.nanoTime();
        try {
            // Execute match in order book
            List<Trade> trades = orderBook.match(order);
            long latencyNanos = System.nanoTime() - startTimeNanos;
            int tradeCount = trades != null ? trades.size() : 0;
            com.exchange.matching.monitoring.MatchingEngineMetrics.getInstance().recordOrderProcessed(latencyNanos, tradeCount);

            if (trades != null && !trades.isEmpty()) {
                LOGGER.info(() -> String.format(
                        "Order %s matched. Generated %d trades.",
                        event.getOrderId(), trades.size()
                ));
                for (Trade trade : trades) {
                    LOGGER.info(() -> String.format(
                            "Trade executed: ID: %s, Qty: %.4f @ %.2f",
                            trade.tradeId(), trade.quantity(), trade.price()
                    ));
                }
            }

            // Gc-free lifecycle management:
            // If the order was completely filled, or it was a market order (which never rests),
            // it can be safely returned to the pool. Limit orders that rest in the book are retained.
            if (order.isFilled() || order.getOrderType() == com.exchange.matching.domain.enums.OrderType.MARKET) {
                orderPool.returnOrder(order);
            }
        } catch (Exception e) {
            LOGGER.severe(() -> "Error during order matching: " + e.getMessage());
            // In case of an unexpected exception, recycle to prevent leaks
            orderPool.returnOrder(order);
        }
    }
}
