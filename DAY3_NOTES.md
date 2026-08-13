# Day 3 Handover Notes: Order Matching Engine

## Overview of Completed Work (Day 3)

Today, we successfully integrated a high-performance **gRPC interface** on top of our low-latency LMAX Disruptor order matching core and Chronicle off-heap persistence layer.

### Key Deliverables Implemented Today:

1. **Protocol Buffer Contracts (`src/main/proto/`)**:
   - `order.proto`: Schema for single orders, side (BUY/SELL), type (LIMIT/MARKET), and status.
   - `trade.proto`: Execution trade reports with buyer/seller order IDs, execution price, and quantity.
   - `market_data.proto`: Order book depth snapshots (Bids/Asks) and ticker information.
   - `order_matching_service.proto`: gRPC service interface defining `SubmitOrder` (unary) and `StreamMarketData` (server-streaming).

2. **Domain Protobuf Mapper (`ProtobufMapper.java`)**:
   - High-efficiency bidirectional conversion between domain entities (`Order`, `OrderBook`, `MarketData`, `Trade`) and gRPC Protobuf messages.

3. **High-Performance gRPC Server (`MatchingEngineServer.java` & `OrderMatchingServiceImpl.java`)**:
   - Non-blocking unary endpoint `SubmitOrder()` publishing order submissions directly onto the LMAX Disruptor ring buffer.
   - Server-streaming endpoint `StreamMarketData()` pushing real-time order book depth updates to subscribed clients.

4. **Connection Pool Management & Backpressure Handling**:
   - Backpressure control via `ServerCallStreamObserver.isReady()`: Slow/congested client stream observers trigger frame-skipping so server memory and Disruptor processing loops are protected from stalling.
   - Connection tracking & lifecycle limits: Enforces max concurrent streaming client connection limits (1,000 max streams) with auto-cleanup on client context cancellation via `setOnCancelHandler()`.

5. **Full Flow Integration Testing**:
   - `OrderMatchingGrpcServerTest`: Validates unary order submission and streaming endpoints.
   - `FullFlowEndToEndIntegrationTest`: End-to-end verification covering: `gRPC SubmitOrder` $\rightarrow$ `Disruptor Ring Buffer` $\rightarrow$ `Matching Engine Execution` $\rightarrow$ `Off-heap Chronicle Map Persistence` $\rightarrow$ `Journal Log` $\rightarrow$ `Real-time Market Data Streaming`.

---

## Known Issues, Rough Edges & Recommendations for Tomorrow (Day 4 Benchmarking)

To the developer working on benchmarking tomorrow:

1. **JVM Off-Heap Memory Sizing & Chronicle Map Limits**:
   - Chronicle Map is configured for off-heap storage. When running high-throughput stress tests (>1,000,000 orders), ensure the JVM is launched with sufficient direct memory allocation: `-XX:MaxDirectMemorySize=4g`.

2. **Thread Pinning vs Netty Event Loop Sizing**:
   - The Disruptor engine uses a `BusySpinWaitStrategy` for sub-microsecond matching latency.
   - Ensure the machine running benchmarks has enough CPU cores allocated so Netty event loops (`grpc-nio-worker-ELG`) and the Disruptor ring buffer single-threaded handler do not contend for the same physical CPU core.

3. **JIT Warmup Phase**:
   - Before taking percentile latency measurements (p50, p90, p99, p99.9), run a warmup batch of at least 50,000 orders to allow the HotSpot C2 compiler to compile critical paths (Disruptor ring buffer handlers & array-backed OrderBook price levels).

4. **Streaming Market Data Coalescing**:
   - Market data updates are published at fixed intervals (e.g., 50ms-100ms). If order throughput exceeds 100,000 orders/sec, the order book state changes rapidly; backpressure frame-skipping handles this gracefully by design.
