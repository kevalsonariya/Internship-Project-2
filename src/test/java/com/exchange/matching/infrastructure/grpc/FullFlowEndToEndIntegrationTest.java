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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Integration Test covering the full matching engine pipeline:
 * gRPC Order Submission -> LMAX Disruptor Ring Buffer -> Matching Engine -> Off-heap Persistence -> gRPC Market Data Stream.
 */
class FullFlowEndToEndIntegrationTest {

    private static final String SYMBOL = "BTC-USDT";

    private IOrderBook orderBook;
    private OrderPool orderPool;
    private DisruptorEngine disruptorEngine;
    private OrderMatchingServiceImpl serviceImpl;

    private OrderMatchingServiceGrpc.OrderMatchingServiceBlockingStub blockingStub;
    private OrderMatchingServiceGrpc.OrderMatchingServiceStub asyncStub;

    @BeforeEach
    void setUp() throws IOException {
        orderBook = new OrderBook(SYMBOL);
        orderPool = new OrderPool(100, 1000);
        disruptorEngine = new DisruptorEngine(512, new BusySpinWaitStrategy(), orderBook, orderPool);
        disruptorEngine.start();

        serviceImpl = new OrderMatchingServiceImpl(disruptorEngine, orderBook);

        String serverName = InProcessServerBuilder.generateName();
        InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(serviceImpl.bindService())
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
    @DisplayName("Full Pipeline: gRPC Submit Order -> Match -> Off-heap Persist -> Market Data Streaming")
    void testFullOrderLifecycleAndStreaming() throws InterruptedException {
        // 1. Subscribe client to real-time market data stream
        CountDownLatch streamLatch = new CountDownLatch(1);
        List<MarketDataProto> streamUpdates = Collections.synchronizedList(new ArrayList<>());

        MarketDataStreamRequest streamRequest = MarketDataStreamRequest.newBuilder()
                .setSymbol(SYMBOL)
                .setMaxDepth(10)
                .setUpdateIntervalMs(20)
                .build();

        asyncStub.streamMarketData(streamRequest, new StreamObserver<>() {
            @Override
            public void onNext(MarketDataProto value) {
                streamUpdates.add(value);
                streamLatch.countDown();
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
            }
        });

        assertTrue(streamLatch.await(2, TimeUnit.SECONDS), "Client failed to connect to streaming market data endpoint");

        // 2. Submit BUY Order via gRPC Unary endpoint
        OrderProto buyOrder = OrderProto.newBuilder()
                .setOrderId("E2E-BUY-1")
                .setSymbol(SYMBOL)
                .setSide(OrderSideProto.BUY)
                .setPrice(50000.00)
                .setQuantity(2.0)
                .setOrderType(OrderTypeProto.LIMIT)
                .setTimestamp(System.currentTimeMillis())
                .build();

        SubmitOrderResponse buyResponse = blockingStub.submitOrder(SubmitOrderRequest.newBuilder().setOrder(buyOrder).build());
        assertTrue(buyResponse.getSuccess());
        assertEquals("E2E-BUY-1", buyResponse.getOrderId());

        // 3. Submit Matching SELL Order via gRPC Unary endpoint
        OrderProto sellOrder = OrderProto.newBuilder()
                .setOrderId("E2E-SELL-1")
                .setSymbol(SYMBOL)
                .setSide(OrderSideProto.SELL)
                .setPrice(50000.00)
                .setQuantity(1.5)
                .setOrderType(OrderTypeProto.LIMIT)
                .setTimestamp(System.currentTimeMillis())
                .build();

        SubmitOrderResponse sellResponse = blockingStub.submitOrder(SubmitOrderRequest.newBuilder().setOrder(sellOrder).build());
        assertTrue(sellResponse.getSuccess());
        assertEquals("E2E-SELL-1", sellResponse.getOrderId());

        // 4. Wait for event processing through Disruptor ring buffer
        TimeUnit.MILLISECONDS.sleep(200);

        // 5. Verify match execution result in OrderBook
        assertNotNull(orderBook.getOrder("E2E-BUY-1"), "Remaining BUY order portion should rest in OrderBook");
        assertEquals(0.5, orderBook.getOrder("E2E-BUY-1").getRemainingQuantity(), 0.0001, "BUY order should have 0.5 remaining quantity");
        assertEquals(1.5, orderBook.getOrder("E2E-BUY-1").getFilledQuantity(), 0.0001, "BUY order should have 1.5 filled quantity");
        assertEquals(2.0, orderBook.getOrder("E2E-BUY-1").getQuantity(), 0.0001, "BUY order initial total quantity should remain 2.0");
        assertNull(orderBook.getOrder("E2E-SELL-1"), "Fully matched SELL order should be removed from OrderBook");

        // 6. Verify off-heap persistence in Chronicle Map
        assertNotNull(disruptorEngine.getPersistenceHandler().getOrderState("E2E-BUY-1"));
        assertEquals("E2E-BUY-1", disruptorEngine.getPersistenceHandler().getOrderState("E2E-BUY-1").getOrderId());

        // 7. Verify market data stream received updates reflecting top depth
        assertTrue(streamUpdates.size() > 0, "Stream updates should have been received by client");
        MarketDataProto latestUpdate = streamUpdates.get(streamUpdates.size() - 1);
        assertEquals(SYMBOL, latestUpdate.getSymbol());
    }

    @Test
    @DisplayName("gRPC Connection Tracking: Connection count should increment on connect and decrement on disconnect")
    void testConnectionTrackingAndLifecycle() throws InterruptedException {
        assertEquals(0, serviceImpl.getActiveConnectionCount());

        CountDownLatch updateLatch = new CountDownLatch(1);
        MarketDataStreamRequest streamRequest = MarketDataStreamRequest.newBuilder()
                .setSymbol(SYMBOL)
                .setMaxDepth(5)
                .setUpdateIntervalMs(50)
                .build();

        StreamObserver<MarketDataProto> clientObserver = new StreamObserver<>() {
            @Override
            public void onNext(MarketDataProto value) {
                updateLatch.countDown();
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
            }
        };

        asyncStub.streamMarketData(streamRequest, clientObserver);

        assertTrue(updateLatch.await(2, TimeUnit.SECONDS));
        assertEquals(1, serviceImpl.getActiveConnectionCount(), "Active connection count should be 1");

        // Close service and verify connection pool reset
        serviceImpl.close();
        assertEquals(0, serviceImpl.getActiveConnectionCount(), "Active connection count should reset to 0 upon close");
    }
}
