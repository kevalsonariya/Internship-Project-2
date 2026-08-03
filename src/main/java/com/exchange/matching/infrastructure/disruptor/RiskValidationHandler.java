package com.exchange.matching.infrastructure.disruptor;

import com.lmax.disruptor.EventHandler;
import java.util.logging.Logger;

/**
 * Handles risk validation of incoming order events in the Disruptor pipeline.
 * <p>
 * Rejects orders having a negative price or non-positive quantity (negative or zero).
 * Mark rejected orders by setting {@link OrderEvent#setRejected(boolean)} to {@code true}.
 * </p>
 */
public class RiskValidationHandler implements EventHandler<OrderEvent> {

    private static final Logger LOGGER = Logger.getLogger(RiskValidationHandler.class.getName());

    /**
     * Processes an event by running validation checks.
     *
     * @param event      the pre-allocated event on the ring buffer
     * @param sequence   the sequence number of the event
     * @param endOfBatch indicates if this is the end of the batch
     */
    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        if (event == null) {
            return;
        }

        // Validate price and quantity rules
        if (event.getPrice() < 0.0) {
            event.setRejected(true);
            LOGGER.warning(() -> String.format(
                    "Order rejected: Negative price. Order ID: %s, Price: %.2f",
                    event.getOrderId(), event.getPrice()
            ));
            return;
        }

        if (event.getQuantity() <= 0.0) {
            event.setRejected(true);
            LOGGER.warning(() -> String.format(
                    "Order rejected: Negative or zero quantity. Order ID: %s, Quantity: %.4f",
                    event.getOrderId(), event.getQuantity()
            ));
        }
    }
}
