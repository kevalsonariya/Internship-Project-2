package com.exchange.matching.infrastructure.disruptor;

import com.lmax.disruptor.EventHandler;
import java.util.logging.Logger;

/**
 * Stub implementation of the Journaling handler responsible for audit logging/durability.
 * <p>
 * In a production system, this handler would append the raw order event to a non-volatile
 * sequential write-ahead log (WAL) for crash recovery and replication.
 * </p>
 */
public class JournalingHandler implements EventHandler<OrderEvent> {

    private static final Logger LOGGER = Logger.getLogger(JournalingHandler.class.getName());

    /**
     * Processes/logs the event for auditing and journaling.
     *
     * @param event      the order event from the ring buffer
     * @param sequence   the sequence number
     * @param endOfBatch indicates if this is the end of the batch
     */
    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        if (event == null) {
            return;
        }

        LOGGER.info(() -> String.format(
                "Journal log: Sequence: %d, Order ID: %s, Symbol: %s, Side: %s, Price: %.2f, Qty: %.4f, Rejected: %b",
                sequence, event.getOrderId(), event.getSymbol(), event.getSide(),
                event.getPrice(), event.getQuantity(), event.isRejected()
        ));
    }
}
