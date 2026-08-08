package com.exchange.matching.infrastructure.disruptor;

import com.exchange.matching.domain.enums.OrderSide;
import com.exchange.matching.domain.enums.OrderStatus;
import com.exchange.matching.domain.enums.OrderType;
import com.exchange.matching.domain.model.Order;
import com.exchange.matching.domain.model.OrderState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceHandlerTest {

    private PersistenceHandler persistenceHandler;

    @BeforeEach
    void setUp() {
        persistenceHandler = new PersistenceHandler(1_000L);
    }

    @AfterEach
    void tearDown() {
        if (persistenceHandler != null) {
            persistenceHandler.close();
        }
    }

    @Test
    void testOnEventPersistsValidOrderEvent() {
        OrderEvent event = new OrderEvent();
        event.setOrderId("ORD-001");
        event.setSymbol("BTC-USDT");
        event.setSide(OrderSide.BUY);
        event.setPrice(50000.00);
        event.setQuantity(1.5);
        event.setOrderType(OrderType.LIMIT);
        event.setTimestamp(1700000000000L);
        event.setRejected(false);

        persistenceHandler.onEvent(event, 1L, true);

        assertEquals(1, persistenceHandler.size());
        OrderState state = persistenceHandler.getOrderState("ORD-001");
        assertNotNull(state);
        assertEquals("ORD-001", state.getOrderId());
        assertEquals("BTC-USDT", state.getSymbol());
        assertEquals(OrderSide.BUY, state.getSide());
        assertEquals(50000.00, state.getPrice());
        assertEquals(1.5, state.getQuantity());
        assertEquals(OrderStatus.NEW, state.getStatus());
    }

    @Test
    void testOnEventSkipsRejectedEvent() {
        OrderEvent event = new OrderEvent();
        event.setOrderId("ORD-REJECTED");
        event.setRejected(true);

        persistenceHandler.onEvent(event, 1L, true);

        assertEquals(0, persistenceHandler.size());
        assertNull(persistenceHandler.getOrderState("ORD-REJECTED"));
    }

    @Test
    void testOnEventSkipsNullOrderId() {
        OrderEvent event = new OrderEvent();
        event.setOrderId(null);
        event.setRejected(false);

        persistenceHandler.onEvent(event, 1L, true);

        assertEquals(0, persistenceHandler.size());
    }

    @Test
    void testFilePersistedHandler(@TempDir Path tempDir) throws IOException {
        File file = tempDir.resolve("chronicle-order-map.dat").toFile();
        try (PersistenceHandler fileHandler = new PersistenceHandler(file, 500L)) {
            OrderEvent event = new OrderEvent();
            event.setOrderId("FILE-ORD-1");
            event.setSymbol("ETH-USDT");
            event.setSide(OrderSide.SELL);
            event.setPrice(3000.00);
            event.setQuantity(5.0);
            event.setOrderType(OrderType.LIMIT);
            event.setTimestamp(1700000000001L);

            fileHandler.onEvent(event, 10L, true);

            assertEquals(1, fileHandler.size());
            OrderState state = fileHandler.getOrderState("FILE-ORD-1");
            assertNotNull(state);
            assertEquals("ETH-USDT", state.getSymbol());
            assertEquals(3000.00, state.getPrice());
        }
    }

    @Test
    void testOrderStateFromDomainOrder() {
        Order order = new Order("ORD-100", "SOL-USDT", OrderSide.BUY, 150.00, 20.0, 1700000000002L, OrderType.LIMIT);
        order.executeFill(5.0);

        OrderState state = OrderState.fromOrder(order);
        assertNotNull(state);
        assertEquals("ORD-100", state.getOrderId());
        assertEquals("SOL-USDT", state.getSymbol());
        assertEquals(150.00, state.getPrice());
        assertEquals(20.0, state.getQuantity());
        assertEquals(5.0, state.getFilledQuantity());
        assertEquals(OrderStatus.PARTIALLY_FILLED, state.getStatus());
        assertFalse(state.isFilled());

        order.executeFill(15.0);
        OrderState stateFilled = OrderState.fromOrder(order);
        assertEquals(OrderStatus.FILLED, stateFilled.getStatus());
        assertTrue(stateFilled.isFilled());
    }
}
