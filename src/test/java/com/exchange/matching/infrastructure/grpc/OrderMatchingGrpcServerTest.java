package com.exchange.matching.infrastructure.grpc;

import com.exchange.matching.domain.enums.OrderSide;
import com.exchange.matching.domain.enums.OrderType;
import com.exchange.matching.domain.model.IOrderBook;
import com.exchange.matching.domain.model.OrderBook;
import com.exchange.matching.infrastructure.disruptor.DisruptorEngine;
import com.exchange.matching.infrastructure.pool.OrderPool;
import com.exchange.matching.protobuf.MarketDataProto;
import com.exchange.matching.protobuf.MarketDataStreamRequest;
import com.exchange.matching.protobuf.OrderMatchingServiceGrpc;
import com.exchange.matching.protobuf.OrderProto;
import com.exchange.matching.protobuf.OrderSideProto;
import com.exchange.matching.protobuf.OrderTypeProto;
import com.exchange.matching.protobuf.SubmitOrderRequest;
import com.exchange.matching.protobuf.SubmitOrderResponse;
import com.lmax.disruptor.BusySpinWaitStrategy;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class OrderMatchingGrpcServerTest {

    private static final String SYMBOL = "BTC-USDT";
    private DisruptorEngine disruptorEngine;
    private IOrderBook orderBook;
    private OrderPool orderPool;
    private OrderMatchingServiceImpl serviceImpl;

    private OrderMatchingServiceGrpc.OrderMatchingServiceBlockingStub blockingStub;
    private OrderMatchingServiceGrpc.OrderMatchingServiceStub asyncStub;

    @BeforeEach
    void setUp() throws IOException {
        orderBook = new OrderBook(SYMBOL);
        orderPool = new OrderPool(100, 500);
        disruptorEngine = new DisruptorEngine(256, new BusySpinWaitStrategy(), orderBook, orderPool);
        disruptorEngine.start();

        serviceImpl = new OrderMatchingServiceImpl(disruptorEngine, orderBook);

        String serverName = InProcessServerBuilder.generateName();
        InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(serviceImpl)
                .build()
                .start();

        var channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();

        blockingStub = OrderMatchingServiceGrpc.newBlockingStub(channel);
        asyncStub = OrderMatchingServiceGrpc.newStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (serviceImpl != null) {
            serviceImpl.close();
        }
        if (disruptorEngine != null) {
            disruptorEngine.shutdown();
        }
    }

    @Test
    @DisplayName("gRPC SubmitOrder endpoint should publish order to Disruptor and update OrderBook & Persistence")
    void testSubmitOrderGrpcEndpoint() throws InterruptedException {
        OrderProto orderProto = OrderProto.newBuilder()
                .setOrderId("GRPC-ORD-1")
                .setSymbol(SYMBOL)
                .setSide(OrderSideProto.BUY)
                .setPrice(52000.00)
                .setQuantity(2.5)
                .setOrderType(OrderTypeProto.LIMIT)
                .setTimestamp(System.currentTimeMillis())
                .build();

        SubmitOrderRequest request = SubmitOrderRequest.newBuilder()
                .setOrder(orderProto)
                .build();

        SubmitOrderResponse response = blockingStub.submitOrder(request);

        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals("GRPC-ORD-1", response.getOrderId());

        // Wait for event to flow through Disruptor pipeline
        TimeUnit.MILLISECONDS.sleep(150);

        // Verify resting order in OrderBook
        assertNotNull(orderBook.getOrder("GRPC-ORD-1"));
        assertEquals(52000.00, orderBook.getOrder("GRPC-ORD-1").getPrice());
        assertEquals(2.5, orderBook.getOrder("GRPC-ORD-1").getQuantity());

        // Verify order persisted in Chronicle Map via PersistenceHandler
        assertNotNull(disruptorEngine.getPersistenceHandler().getOrderState("GRPC-ORD-1"));
        assertEquals("GRPC-ORD-1", disruptorEngine.getPersistenceHandler().getOrderState("GRPC-ORD-1").getOrderId());
    }

    @Test
    @DisplayName("gRPC StreamMarketData endpoint should stream OrderBook depth updates to client")
    void testStreamMarketDataGrpcEndpoint() throws InterruptedException {
        // Place resting limit orders in order book
        orderBook.addOrder(new com.exchange.matching.domain.model.Order("B1", SYMBOL, OrderSide.BUY, 50000.0, 1.0, System.currentTimeMillis(), OrderType.LIMIT));
        orderBook.addOrder(new com.exchange.matching.domain.model.Order("A1", SYMBOL, OrderSide.SELL, 51000.0, 2.0, System.currentTimeMillis(), OrderType.LIMIT));

        CountDownLatch latch = new CountDownLatch(2);
        List<MarketDataProto> receivedUpdates = new ArrayList<>();

        MarketDataStreamRequest request = MarketDataStreamRequest.newBuilder()
                .setSymbol(SYMBOL)
                .setMaxDepth(5)
                .setUpdateIntervalMs(50)
                .build();

        asyncStub.streamMarketData(request, new StreamObserver<>() {
            @Override
            public void onNext(MarketDataProto value) {
                receivedUpdates.add(value);
                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
            }
        });

        // Wait for streaming observer to receive at least 2 snapshot updates
        assertTrue(latch.await(3, TimeUnit.SECONDS), "Streaming client timed out receiving market data updates");
        assertTrue(receivedUpdates.size() >= 2);

        MarketDataProto update = receivedUpdates.get(0);
        assertEquals(SYMBOL, update.getSymbol());
        assertEquals(1, update.getBidsCount());
        assertEquals(50000.0, update.getBids(0).getPrice());
        assertEquals(1, update.getAsksCount());
        assertEquals(51000.0, update.getAsks(0).getPrice());
    }
}
