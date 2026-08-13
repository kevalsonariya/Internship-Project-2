package com.exchange.matching.infrastructure.grpc;

import com.exchange.matching.domain.model.IOrderBook;
import com.exchange.matching.domain.model.OrderBook;
import com.exchange.matching.infrastructure.disruptor.DisruptorEngine;
import com.exchange.matching.infrastructure.pool.OrderPool;
import com.lmax.disruptor.BusySpinWaitStrategy;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main application bootstrap and gRPC server entry point for the Order Matching Engine.
 * <p>
 * Initializes domain OrderBook, OrderPool, LMAX Disruptor engine pipeline, off-heap persistence,
 * and starts the gRPC server listening for client RPC requests.
 * </p>
 */
public class MatchingEngineServer {

    private static final Logger LOGGER = Logger.getLogger(MatchingEngineServer.class.getName());
    public static final int DEFAULT_PORT = 9090;

    private final int port;
    private final Server server;
    private final DisruptorEngine disruptorEngine;
    private final OrderMatchingServiceImpl serviceImpl;
    private final IOrderBook orderBook;
    private final OrderPool orderPool;

    /**
     * Constructs a MatchingEngineServer listening on default port 9090 for symbol BTC-USDT.
     */
    public MatchingEngineServer() {
        this(DEFAULT_PORT, "BTC-USDT");
    }

    /**
     * Constructs a MatchingEngineServer listening on specified port for specified trading symbol.
     *
     * @param port   port to bind gRPC server
     * @param symbol trading instrument symbol
     */
    public MatchingEngineServer(int port, String symbol) {
        this.port = port;
        this.orderBook = new OrderBook(symbol);
        this.orderPool = new OrderPool(1000, 10000);
        this.disruptorEngine = new DisruptorEngine(1024, new BusySpinWaitStrategy(), orderBook, orderPool);
        this.serviceImpl = new OrderMatchingServiceImpl(disruptorEngine, orderBook);

        this.server = ServerBuilder.forPort(port)
                .addService(serviceImpl.bindService())
                .build();
    }

    /**
     * Constructs a MatchingEngineServer using existing core engine components.
     *
     * @param port            port to bind gRPC server
     * @param disruptorEngine disruptor engine instance
     * @param orderBook       order book instance
     */
    public MatchingEngineServer(int port, DisruptorEngine disruptorEngine, IOrderBook orderBook) {
        this.port = port;
        this.orderBook = orderBook;
        this.orderPool = null;
        this.disruptorEngine = disruptorEngine;
        this.serviceImpl = new OrderMatchingServiceImpl(disruptorEngine, orderBook);

        this.server = ServerBuilder.forPort(port)
                .addService(serviceImpl.bindService())
                .build();
    }

    /**
     * Starts the Disruptor engine and gRPC server.
     *
     * @throws IOException if server fails to bind to port
     */
    public void start() throws IOException {
        disruptorEngine.start();
        server.start();
        LOGGER.info(() -> String.format("MatchingEngineServer started successfully listening on port %d for symbol %s",
                port, orderBook.getSymbol()));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutdown signal received. Stopping MatchingEngineServer...");
            try {
                MatchingEngineServer.this.stop();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error during server shutdown", e);
            }
        }));
    }

    /**
     * Stops the gRPC server and shuts down the Disruptor pipeline gracefully.
     */
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (serviceImpl != null) {
            serviceImpl.close();
        }
        if (disruptorEngine != null) {
            disruptorEngine.shutdown();
        }
        LOGGER.info("MatchingEngineServer stopped cleanly.");
    }

    /**
     * Waits for the gRPC server to exit.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Gets the bound port.
     *
     * @return server port
     */
    public int getPort() {
        return port;
    }

    /**
     * Main entry point to launch the Matching Engine gRPC server application.
     *
     * @param args optional command line arguments (args[0] = port)
     */
    public static void main(String[] args) {
        int serverPort = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                serverPort = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid port format provided in command line arguments. Falling back to default port " + DEFAULT_PORT);
            }
        }

        try {
            MatchingEngineServer server = new MatchingEngineServer(serverPort, "BTC-USDT");
            server.start();
            server.blockUntilShutdown();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fatal error launching MatchingEngineServer", e);
            System.exit(1);
        }
    }
}
