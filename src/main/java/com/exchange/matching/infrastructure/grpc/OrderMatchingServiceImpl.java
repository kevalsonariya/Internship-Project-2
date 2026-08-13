package com.exchange.matching.infrastructure.grpc;

import com.exchange.matching.domain.enums.OrderSide;
import com.exchange.matching.domain.enums.OrderType;
import com.exchange.matching.domain.model.IOrderBook;
import com.exchange.matching.domain.model.MarketData;
import com.exchange.matching.infrastructure.disruptor.DisruptorEngine;
import com.exchange.matching.infrastructure.protobuf.ProtobufMapper;
import com.exchange.matching.protobuf.MarketDataProto;
import com.exchange.matching.protobuf.MarketDataStreamRequest;
import com.exchange.matching.protobuf.OrderMatchingServiceGrpc;
import com.exchange.matching.protobuf.OrderProto;
import com.exchange.matching.protobuf.SubmitOrderRequest;
import com.exchange.matching.protobuf.SubmitOrderResponse;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * gRPC Service implementation providing order submission to the Disruptor Ring Buffer
 * and real-time streaming of Order Book depth updates.
 */
public class OrderMatchingServiceImpl extends OrderMatchingServiceGrpc.OrderMatchingServiceImplBase implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(OrderMatchingServiceImpl.class.getName());
    public static final int MAX_STREAMING_CONNECTIONS = 1000;

    private final DisruptorEngine disruptorEngine;
    private final IOrderBook orderBook;
    private final ScheduledExecutorService scheduler;
    private final AtomicLong orderIdSequence = new AtomicLong(1000L);

    private final Set<StreamObserver<MarketDataProto>> activeSubscriptions = ConcurrentHashMap.newKeySet();
    private final AtomicInteger activeConnectionCount = new AtomicInteger(0);

    /**
     * Constructs an OrderMatchingServiceImpl with required Disruptor engine and order book references.
     *
     * @param disruptorEngine disruptor engine for high-throughput ring buffer publishing
     * @param orderBook       order book for market data depth queries
     */
    public OrderMatchingServiceImpl(DisruptorEngine disruptorEngine, IOrderBook orderBook) {
        this.disruptorEngine = Objects.requireNonNull(disruptorEngine, "disruptorEngine must not be null");
        this.orderBook = Objects.requireNonNull(orderBook, "orderBook must not be null");
        this.scheduler = Executors.newScheduledThreadPool(4, r -> {
            Thread thread = new Thread(r, "grpc-marketdata-streamer");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Publishes incoming order requests onto the Disruptor ring buffer.
     *
     * @param request          SubmitOrderRequest containing OrderProto payload
     * @param responseObserver observer for returning submission confirmation
     */
    @Override
    public void submitOrder(SubmitOrderRequest request, StreamObserver<SubmitOrderResponse> responseObserver) {
        try {
            if (request == null || !request.hasOrder()) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("Request or Order payload must not be null")
                        .asRuntimeException());
                return;
            }

            OrderProto orderProto = request.getOrder();
            String orderId = (orderProto.getOrderId() != null && !orderProto.getOrderId().trim().isEmpty())
                    ? orderProto.getOrderId()
                    : "ORD-" + System.currentTimeMillis() + "-" + orderIdSequence.getAndIncrement();

            String symbol = orderProto.getSymbol() != null && !orderProto.getSymbol().trim().isEmpty()
                    ? orderProto.getSymbol()
                    : orderBook.getSymbol();

            OrderSide side = ProtobufMapper.toDomain(orderProto.getSide());
            OrderType type = ProtobufMapper.toDomain(orderProto.getOrderType());
            double price = orderProto.getPrice();
            double quantity = orderProto.getQuantity();
            long timestamp = orderProto.getTimestamp() > 0 ? orderProto.getTimestamp() : System.currentTimeMillis();

            // Publish onto low-latency Disruptor ring buffer
            disruptorEngine.getProducer().onData(orderId, symbol, side, price, quantity, timestamp, type);

            SubmitOrderResponse response = SubmitOrderResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Order successfully published to Disruptor ring buffer")
                    .setOrderId(orderId)
                    .setTimestamp(timestamp)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            LOGGER.fine(() -> String.format("gRPC SubmitOrder: ID=%s, Symbol=%s, Side=%s, Price=%.2f, Qty=%.4f",
                    orderId, symbol, side, price, quantity));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing gRPC SubmitOrder request", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to publish order to Disruptor: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Streams real-time Order Book depth snapshots / market data updates to connected clients
     * with backpressure awareness and connection tracking.
     *
     * @param request          MarketDataStreamRequest detailing symbol, depth, and update frequency
     * @param responseObserver observer for streaming MarketDataProto updates
     */
    @Override
    public void streamMarketData(MarketDataStreamRequest request, StreamObserver<MarketDataProto> responseObserver) {
        if (activeConnectionCount.get() >= MAX_STREAMING_CONNECTIONS) {
            LOGGER.warning("Refusing new market data streaming client connection: limit of " + MAX_STREAMING_CONNECTIONS + " reached.");
            responseObserver.onError(Status.RESOURCE_EXHAUSTED
                    .withDescription("Maximum streaming connection limit reached (" + MAX_STREAMING_CONNECTIONS + ")")
                    .asRuntimeException());
            return;
        }

        String symbol = (request != null && request.getSymbol() != null && !request.getSymbol().trim().isEmpty())
                ? request.getSymbol()
                : orderBook.getSymbol();

        int maxDepth = (request != null && request.getMaxDepth() > 0) ? request.getMaxDepth() : 10;
        int intervalMs = (request != null && request.getUpdateIntervalMs() > 0) ? request.getUpdateIntervalMs() : 100;

        LOGGER.info(() -> String.format("Client subscribed to Market Data Stream: Symbol=%s, Depth=%d, Interval=%dms. Total connections: %d",
                symbol, maxDepth, intervalMs, activeConnectionCount.get() + 1));

        ServerCallStreamObserver<MarketDataProto> serverCallStreamObserver =
                (responseObserver instanceof ServerCallStreamObserver)
                        ? (ServerCallStreamObserver<MarketDataProto>) responseObserver
                        : null;

        activeSubscriptions.add(responseObserver);
        activeConnectionCount.incrementAndGet();

        Runnable cleanup = () -> {
            if (activeSubscriptions.remove(responseObserver)) {
                activeConnectionCount.decrementAndGet();
                LOGGER.info("Market data streaming client connection closed. Remaining active connections: " + activeConnectionCount.get());
            }
        };

        if (serverCallStreamObserver != null) {
            serverCallStreamObserver.setOnCancelHandler(cleanup);
        }

        Context currentContext = Context.current();

        ScheduledFuture<?> streamingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (currentContext.isCancelled()) {
                    LOGGER.info("gRPC client context cancelled. Stopping market data stream.");
                    cleanup.run();
                    throw new RuntimeException("Client cancelled");
                }

                // Backpressure Check: Skip emitting update frame if slow client buffer is full
                if (serverCallStreamObserver != null && !serverCallStreamObserver.isReady()) {
                    LOGGER.fine("gRPC client stream observer not ready (backpressure active). Skipping snapshot frame.");
                    return;
                }

                MarketData depth = orderBook.getDepth(maxDepth);
                MarketDataProto proto = ProtobufMapper.toProto(depth);

                synchronized (responseObserver) {
                    responseObserver.onNext(proto);
                }
            } catch (Exception e) {
                LOGGER.fine("Market data streaming task terminated: " + e.getMessage());
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);

        currentContext.addListener(context -> {
            streamingTask.cancel(true);
            cleanup.run();
            try {
                responseObserver.onCompleted();
            } catch (Exception ignored) {
            }
        }, scheduler);
    }

    /**
     * Gets current count of active streaming client connections.
     *
     * @return active connection count
     */
    public int getActiveConnectionCount() {
        return activeConnectionCount.get();
    }

    @Override
    public void close() {
        for (StreamObserver<MarketDataProto> observer : activeSubscriptions) {
            try {
                observer.onCompleted();
            } catch (Exception ignored) {
            }
        }
        activeSubscriptions.clear();
        activeConnectionCount.set(0);

        if (!scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }
}
